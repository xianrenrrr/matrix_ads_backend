package com.example.demo.ai.seg.yolo;

import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.OverlayBox;
import com.example.demo.ai.seg.dto.OverlayShape;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class YoloV8SegService implements SegmentationService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${YOLO_API_URL:http://localhost:5000/detect}")
    private String yoloApiUrl;
    
    @Value("${ai.overlay.minConf:0.60}")
    private double minConfidence;
    
    @Value("${ai.overlay.minArea:0.02}")
    private double minArea;
    
    @Value("${ai.overlay.maxObjects:4}")
    private int maxObjects;
    
    public YoloV8SegService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public List<OverlayShape> detect(String keyframeUrl) {
        try {
            Map<String, String> request = new HashMap<>();
            request.put("image_url", keyframeUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                yoloApiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return parseYoloResponse(response.getBody());
            }
        } catch (Exception e) {
            System.err.println("YOLO detection failed: " + e.getMessage());
        }
        
        return Collections.emptyList();
    }
    
    private List<OverlayShape> parseYoloResponse(String responseBody) {
        List<OverlayShape> shapes = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode detections = root.path("detections");
            
            if (detections.isArray()) {
                for (JsonNode detection : detections) {
                    double confidence = detection.path("confidence").asDouble();
                    double x = detection.path("x").asDouble();
                    double y = detection.path("y").asDouble();
                    double w = detection.path("width").asDouble();
                    double h = detection.path("height").asDouble();
                    String label = detection.path("class").asText();
                    
                    double area = w * h;
                    
                    if (confidence >= minConfidence && area >= minArea) {
                        shapes.add(new OverlayBox(
                            label,
                            "",  // labelZh will be filled later
                            confidence,
                            x, y, w, h
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse YOLO response: " + e.getMessage());
        }
        
        return postProcessShapes(shapes);
    }
    
    private List<OverlayShape> postProcessShapes(List<OverlayShape> shapes) {
        // Dedup by label (keep highest conf×area)
        Map<String, OverlayShape> bestByLabel = new HashMap<>();
        
        for (OverlayShape shape : shapes) {
            String label = shape.label();
            double score = shape.confidence();
            
            if (shape instanceof OverlayBox box) {
                score *= (box.w() * box.h());
            }
            
            OverlayShape existing = bestByLabel.get(label);
            if (existing == null) {
                bestByLabel.put(label, shape);
            } else {
                double existingScore = existing.confidence();
                if (existing instanceof OverlayBox existingBox) {
                    existingScore *= (existingBox.w() * existingBox.h());
                }
                
                if (score > existingScore) {
                    bestByLabel.put(label, shape);
                }
            }
        }
        
        // Sort by conf×area and take top-K
        return bestByLabel.values().stream()
            .sorted((a, b) -> {
                double scoreA = a.confidence();
                double scoreB = b.confidence();
                
                if (a instanceof OverlayBox boxA) {
                    scoreA *= (boxA.w() * boxA.h());
                }
                if (b instanceof OverlayBox boxB) {
                    scoreB *= (boxB.w() * boxB.h());
                }
                
                return Double.compare(scoreB, scoreA);
            })
            .limit(maxObjects)
            .collect(Collectors.toList());
    }
}