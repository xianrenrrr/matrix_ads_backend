package com.example.demo.ai.seg.paddledet;

import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaddleDetSegService implements SegmentationService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${PADDLE_SEG_URL:https://paddledet-service.onrender.com/detect}")
    private String paddleSegUrl;
    
    @Value("${PADDLE_SEG_TOKEN:}")
    private String paddleSegToken;
    
    @Value("${ai.overlay.minConf:0.60}")
    private double minConfidence;
    
    @Value("${ai.overlay.minArea:0.02}")
    private double minArea;
    
    @Value("${ai.overlay.maxObjects:4}")
    private int maxObjects;
    
    public PaddleDetSegService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public List<OverlayShape> detect(String keyframeUrl) {
        try {
            // Build request body matching new API format
            Map<String, Object> request = new HashMap<>();
            request.put("imageUrl", keyframeUrl);
            request.put("maxObjects", maxObjects);
            request.put("minConf", minConfidence);
            request.put("minArea", minArea);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Add Authorization header if token is configured
            if (paddleSegToken != null && !paddleSegToken.trim().isEmpty()) {
                headers.set("Authorization", "Bearer " + paddleSegToken);
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                paddleSegUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return parsePaddleResponse(response.getBody());
            }
        } catch (Exception e) {
            System.err.println("PaddleDet detection failed: " + e.getMessage());
        }
        
        return Collections.emptyList();
    }
    
    private List<OverlayShape> parsePaddleResponse(String responseBody) {
        List<OverlayShape> shapes = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // Parse polygons from new API response format
            JsonNode polygons = root.path("polygons");
            if (polygons.isArray()) {
                for (JsonNode polygon : polygons) {
                    String label = polygon.path("label").asText();
                    String labelZh = polygon.path("labelZh").asText();
                    double confidence = polygon.path("confidence").asDouble();
                    
                    List<Point> points = new ArrayList<>();
                    JsonNode pointsArray = polygon.path("points");
                    if (pointsArray.isArray()) {
                        for (JsonNode point : pointsArray) {
                            double x = point.path("x").asDouble();
                            double y = point.path("y").asDouble();
                            points.add(new Point(x, y));
                        }
                    }
                    
                    if (!points.isEmpty()) {
                        shapes.add(new OverlayPolygon(
                            label,
                            labelZh,
                            confidence,
                            points
                        ));
                    }
                }
            }
            
            // Parse boxes from new API response format
            JsonNode boxes = root.path("boxes");
            if (boxes.isArray()) {
                for (JsonNode box : boxes) {
                    String label = box.path("label").asText();
                    String labelZh = box.path("labelZh").asText();
                    double confidence = box.path("confidence").asDouble();
                    double x = box.path("x").asDouble();
                    double y = box.path("y").asDouble();
                    double w = box.path("w").asDouble();
                    double h = box.path("h").asDouble();
                    
                    shapes.add(new OverlayBox(
                        label,
                        labelZh,
                        confidence,
                        x, y, w, h
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse PaddleDet response: " + e.getMessage());
        }
        
        return postProcessShapes(shapes);
    }
    
    private double calculatePolygonArea(List<Point> points) {
        if (points.size() < 3) return 0;
        
        double area = 0;
        int n = points.size();
        
        for (int i = 0; i < n; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get((i + 1) % n);
            area += p1.x() * p2.y() - p2.x() * p1.y();
        }
        
        return Math.abs(area) / 2.0;
    }
    
    private List<OverlayShape> postProcessShapes(List<OverlayShape> shapes) {
        // Dedup by label (keep highest conf×area)
        Map<String, OverlayShape> bestByLabel = new HashMap<>();
        
        for (OverlayShape shape : shapes) {
            String label = shape.label();
            double score = shape.confidence();
            double area = 0;
            
            if (shape instanceof OverlayBox box) {
                area = box.w() * box.h();
            } else if (shape instanceof OverlayPolygon polygon) {
                area = calculatePolygonArea(polygon.points());
            }
            
            score *= area;
            
            OverlayShape existing = bestByLabel.get(label);
            if (existing == null) {
                bestByLabel.put(label, shape);
            } else {
                double existingScore = existing.confidence();
                double existingArea = 0;
                
                if (existing instanceof OverlayBox existingBox) {
                    existingArea = existingBox.w() * existingBox.h();
                } else if (existing instanceof OverlayPolygon existingPolygon) {
                    existingArea = calculatePolygonArea(existingPolygon.points());
                }
                
                existingScore *= existingArea;
                
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
                } else if (a instanceof OverlayPolygon polygonA) {
                    scoreA *= calculatePolygonArea(polygonA.points());
                }
                
                if (b instanceof OverlayBox boxB) {
                    scoreB *= (boxB.w() * boxB.h());
                } else if (b instanceof OverlayPolygon polygonB) {
                    scoreB *= calculatePolygonArea(polygonB.points());
                }
                
                return Double.compare(scoreB, scoreA);
            })
            .limit(maxObjects)
            .collect(Collectors.toList());
    }
}