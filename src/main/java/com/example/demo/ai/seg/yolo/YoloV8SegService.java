package com.example.demo.ai.seg.yolo;

import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.OverlayBox;
import com.example.demo.ai.seg.dto.OverlayShape;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.stream.Collectors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import com.example.demo.ai.shared.GcsFileResolver;

@Service
public class YoloV8SegService implements SegmentationService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Legacy local YOLO endpoint (kept for backward compatibility)
    @Value("${YOLO_API_URL:http://localhost:5000/detect}")
    private String yoloApiUrl;

    // HuggingFace Inference API endpoint and key (preferred path)
    @Value("${AI_YOLO_ENDPOINT:${ai.providers.yolo.endpoint:https://api-inference.huggingface.co/models/facebook/detr-resnet-50}}")
    private String hfEndpoint;

    @Value("${AI_YOLO_API_KEY:${ai.providers.yolo.api-key:}}")
    private String hfApiKey;
    
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
    
    @Autowired(required = false)
    private GcsFileResolver gcsFileResolver;

    @Override
    public List<OverlayShape> detect(String keyframeUrl) {
        // Prefer HuggingFace API; no localhost fallback
        if (hfEndpoint != null && !hfEndpoint.isBlank() && hfApiKey != null && !hfApiKey.isBlank()) {
            try {
                return detectWithHuggingFace(keyframeUrl);
            } catch (Exception e) {
                System.err.println("YOLO(HF) detection failed: " + e.getMessage());
                return Collections.emptyList();
            }
        }
        System.err.println("YOLO(HF) not configured. Set AI_YOLO_API_KEY and optionally AI_YOLO_ENDPOINT.");
        return Collections.emptyList();
    }

    private List<OverlayShape> detectWithHuggingFace(String keyframeUrl) throws IOException {
        // 1) Load image bytes and dimensions
        byte[] bytes;
        int imgW; int imgH;
        File localFile = null;

        // Try to resolve via GcsFileResolver first (handles gs:// and GCS HTTPS)
        if (gcsFileResolver != null && (keyframeUrl.startsWith("gs://") || keyframeUrl.startsWith("https://storage.googleapis.com/"))) {
            try (GcsFileResolver.ResolvedFile rf = gcsFileResolver.resolve(keyframeUrl)) {
                localFile = new File(rf.getPathAsString());
                bytes = Files.readAllBytes(localFile.toPath());
                BufferedImage bi = ImageIO.read(localFile);
                if (bi == null) throw new IOException("Failed to read image: " + localFile);
                imgW = bi.getWidth(); imgH = bi.getHeight();
            }
        } else if (keyframeUrl.startsWith("file:")) {
            // Local file URL
            String path = keyframeUrl.replaceFirst("^file:(//)?", "");
            localFile = new File(path);
            if (!localFile.exists()) throw new IOException("Local frame file not found: " + path);
            bytes = Files.readAllBytes(localFile.toPath());
            BufferedImage bi = ImageIO.read(localFile);
            if (bi == null) throw new IOException("Failed to read image: " + localFile);
            imgW = bi.getWidth(); imgH = bi.getHeight();
        } else if (keyframeUrl.startsWith("http://") || keyframeUrl.startsWith("https://")) {
            // General HTTP fetch
            ResponseEntity<byte[]> imgResp = restTemplate.exchange(keyframeUrl, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
            if (!imgResp.getStatusCode().is2xxSuccessful() || imgResp.getBody() == null) {
                throw new IOException("Failed to fetch image: HTTP " + imgResp.getStatusCodeValue());
            }
            bytes = imgResp.getBody();
            // Read dimensions from bytes
            BufferedImage bi = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (bi == null) throw new IOException("Failed to decode image bytes");
            imgW = bi.getWidth(); imgH = bi.getHeight();
        } else {
            // Treat as local path string
            localFile = new File(keyframeUrl);
            if (!localFile.exists()) throw new IOException("Frame path not found: " + keyframeUrl);
            bytes = Files.readAllBytes(localFile.toPath());
            BufferedImage bi = ImageIO.read(localFile);
            if (bi == null) throw new IOException("Failed to read image: " + localFile);
            imgW = bi.getWidth(); imgH = bi.getHeight();
        }

        // 2) Call HF inference API (object detection)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + hfApiKey);
        HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            hfEndpoint,
            HttpMethod.POST,
            entity,
            String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IOException("HF responded " + response.getStatusCodeValue() + ": " + truncate(response.getBody()));
        }

        // 3) Parse HF response and normalize boxes
        return parseHuggingFaceResponse(response.getBody(), imgW, imgH);
    }

    private List<OverlayShape> parseHuggingFaceResponse(String body, int imgW, int imgH) {
        List<OverlayShape> shapes = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode arr = root.isArray() ? root : root.path("predictions");
            if (arr != null && arr.isArray()) {
                for (JsonNode det : arr) {
                    double score = det.path("score").asDouble(det.path("confidence").asDouble(0.0));
                    String label = det.path("label").asText(det.path("class").asText(""));
                    JsonNode box = det.path("box");
                    double xmin, ymin, xmax, ymax;
                    if (box.isObject()) {
                        xmin = box.path("xmin").asDouble();
                        ymin = box.path("ymin").asDouble();
                        xmax = box.path("xmax").asDouble();
                        ymax = box.path("ymax").asDouble();
                    } else {
                        // Fallback to x,y,w,h if provided
                        xmin = det.path("x").asDouble();
                        ymin = det.path("y").asDouble();
                        double w = det.path("width").asDouble();
                        double h = det.path("height").asDouble();
                        xmax = xmin + w; ymax = ymin + h;
                    }
                    if (imgW <= 0 || imgH <= 0) continue;
                    double nx = clamp01(xmin / imgW);
                    double ny = clamp01(ymin / imgH);
                    double nw = clamp01((xmax - xmin) / imgW);
                    double nh = clamp01((ymax - ymin) / imgH);

                    double area = nw * nh;
                    if (score >= minConfidence && area >= minArea) {
                        shapes.add(new OverlayBox(label, "", score, nx, ny, nw, nh));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse YOLO(HF) response: " + e.getMessage());
        }
        return postProcessShapes(shapes);
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private String truncate(String s) { return (s == null) ? null : (s.length() > 300 ? s.substring(0,300) + "…" : s); }
    
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
