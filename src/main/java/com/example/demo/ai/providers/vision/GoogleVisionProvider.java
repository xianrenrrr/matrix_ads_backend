package com.example.demo.ai.providers.vision;

import com.example.demo.ai.core.AIModelType;
import com.example.demo.ai.core.AIResponse;
import com.example.demo.model.SceneSegment;
import com.example.demo.util.FirebaseCredentialsUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoogleVisionProvider implements VisionProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleVisionProvider.class);
    
    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;
    
    @Value("${ai.overlay.polygons.enabled:true}")
    private boolean polygonsEnabled;
    
    @Value("${ai.overlay.polygons.maxShapes:4}")
    private int maxShapes;
    
    @Value("${ai.overlay.polygons.minArea:0.02}")
    private float minArea;
    
    @Value("${ai.overlay.confidenceThreshold:0.6}")
    private float confidenceThreshold;
    
    /**
     * Detect objects with polygon boundaries in a keyframe image
     * @param imageUrl GCS URL of the keyframe image
     * @return List of polygon overlays with normalized coordinates
     */
    @Override
    public AIResponse<List<VisionProvider.ObjectPolygon>> detectObjectPolygons(String imageUrl) {
        long startTime = System.currentTimeMillis();
        
        if (!polygonsEnabled || imageUrl == null || imageUrl.isEmpty()) {
            return AIResponse.success(new ArrayList<>(), getProviderName(), getModelType());
        }
        
        try {
            List<OverlayPolygon> legacyPolygons = detectObjectPolygonsLegacy(imageUrl);
            List<VisionProvider.ObjectPolygon> polygons = legacyPolygons.stream()
                .map(this::convertToVisionPolygon)
                .collect(Collectors.toList());
                
            AIResponse<List<VisionProvider.ObjectPolygon>> response = 
                AIResponse.success(polygons, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;
        } catch (Exception e) {
            logger.error("Failed to detect object polygons from keyframe {}: {}", imageUrl, e.getMessage(), e);
            return AIResponse.error("Vision API error: " + e.getMessage());
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    public List<OverlayPolygon> detectObjectPolygonsLegacy(String imageUrl) {
        if (!polygonsEnabled || imageUrl == null || imageUrl.isEmpty()) {
            return new ArrayList<>();
        }
        
        try (ImageAnnotatorClient visionClient = createVisionClient()) {
            // Create image source from GCS URL
            ImageSource imageSource = ImageSource.newBuilder()
                .setGcsImageUri(imageUrl)
                .build();
            
            Image image = Image.newBuilder()
                .setSource(imageSource)
                .build();
            
            // Configure object localization feature
            Feature feature = Feature.newBuilder()
                .setType(Feature.Type.OBJECT_LOCALIZATION)
                .build();
            
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build();
            
            // Perform detection
            BatchAnnotateImagesResponse response = visionClient.batchAnnotateImages(
                BatchAnnotateImagesRequest.newBuilder()
                    .addRequests(request)
                    .build()
            );
            
            AnnotateImageResponse imageResponse = response.getResponses(0);
            
            if (imageResponse.hasError()) {
                logger.warn("Cloud Vision API error for {}: {}", imageUrl, imageResponse.getError().getMessage());
                return new ArrayList<>();
            }
            
            // Process detected objects
            List<OverlayPolygon> polygons = imageResponse.getLocalizedObjectAnnotationsList()
                .stream()
                .filter(obj -> obj.getScore() >= confidenceThreshold)
                .map(this::convertToOverlayPolygon)
                .filter(Objects::nonNull)
                .filter(this::isPolygonValid)
                .sorted((a, b) -> Float.compare(b.getConfidence(), a.getConfidence())) // Descending confidence
                .limit(maxShapes)
                .collect(Collectors.toList());
            
            logger.info("Detected {} polygon overlays from keyframe: {}", polygons.size(), imageUrl);
            return polygons;
            
        } catch (Exception e) {
            logger.error("Failed to detect object polygons from keyframe {}: {}", imageUrl, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Convert Vision API LocalizedObjectAnnotation to OverlayPolygon
     */
    private OverlayPolygon convertToOverlayPolygon(LocalizedObjectAnnotation annotation) {
        try {
            String label = annotation.getName();
            float confidence = annotation.getScore();
            BoundingPoly boundingPoly = annotation.getBoundingPoly();
            
            // Extract normalized vertices from boundingPoly
            List<OverlayPolygon.Point> points = boundingPoly.getNormalizedVerticesList()
                .stream()
                .map(vertex -> new OverlayPolygon.Point(
                    clamp(vertex.getX(), 0f, 1f),
                    clamp(vertex.getY(), 0f, 1f)
                ))
                .collect(Collectors.toList());
            
            if (points.size() < 3) {
                logger.warn("Polygon has less than 3 points for object: {}", label);
                return null;
            }
            
            return new OverlayPolygon(label, confidence, points);
            
        } catch (Exception e) {
            logger.warn("Failed to convert annotation to overlay polygon: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Validate polygon (minimum area, valid coordinates)
     */
    private boolean isPolygonValid(OverlayPolygon polygon) {
        if (polygon.getPoints().size() < 3) {
            return false;
        }
        
        // Calculate approximate area using shoelace formula
        float area = calculatePolygonArea(polygon.getPoints());
        
        if (area < minArea) {
            logger.debug("Polygon area {} below threshold {} for label: {}", 
                area, minArea, polygon.getLabel());
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculate polygon area using shoelace formula
     */
    private float calculatePolygonArea(List<OverlayPolygon.Point> points) {
        if (points.size() < 3) return 0f;
        
        float area = 0f;
        int n = points.size();
        
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += points.get(i).getX() * points.get(j).getY();
            area -= points.get(j).getX() * points.get(i).getY();
        }
        
        return Math.abs(area) / 2f;
    }
    
    /**
     * Clamp value to range [min, max]
     */
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    // =========================
    // VisionProvider Interface Implementation
    // =========================
    
    @Override
    public AIResponse<List<SceneSegment>> detectSceneChanges(String videoUrl, double threshold) {
        // For now, delegate to FFmpegSceneDetectionService or return not supported
        return AIResponse.error("Scene change detection not supported by Google Vision API");
    }
    
    @Override
    public AIResponse<String> extractKeyframe(String videoUrl, long timestampMs) {
        // For now, return not supported - this would need Video Intelligence API
        return AIResponse.error("Keyframe extraction not supported by Google Vision API");
    }
    
    // =========================
    // AIModelProvider Interface Implementation
    // =========================
    
    @Override
    public AIModelType getModelType() {
        return AIModelType.VISION;
    }
    
    @Override
    public String getProviderName() {
        return "GoogleVision";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            return firebaseCredentialsUtil != null && firebaseCredentialsUtil.getCredentials() != null;
        } catch (Exception e) {
            logger.warn("Google Vision provider not available: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("polygonsEnabled", polygonsEnabled);
        config.put("maxShapes", maxShapes);
        config.put("minArea", minArea);
        config.put("confidenceThreshold", confidenceThreshold);
        return config;
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        if (config.containsKey("polygonsEnabled")) {
            this.polygonsEnabled = (Boolean) config.get("polygonsEnabled");
        }
        if (config.containsKey("maxShapes")) {
            this.maxShapes = (Integer) config.get("maxShapes");
        }
        if (config.containsKey("minArea")) {
            this.minArea = ((Number) config.get("minArea")).floatValue();
        }
        if (config.containsKey("confidenceThreshold")) {
            this.confidenceThreshold = ((Number) config.get("confidenceThreshold")).floatValue();
        }
    }
    
    @Override
    public int getPriority() {
        return 50; // Medium priority - YOLO would be higher
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        switch (operation) {
            case "detectObjectPolygons":
                return true;
            case "detectSceneChanges":
            case "extractKeyframe":
                return false; // Not supported
            default:
                return false;
        }
    }
    
    // =========================
    // Helper Methods
    // =========================
    
    /**
     * Convert legacy OverlayPolygon to VisionProvider.ObjectPolygon
     */
    private VisionProvider.ObjectPolygon convertToVisionPolygon(OverlayPolygon legacyPolygon) {
        List<VisionProvider.ObjectPolygon.Point> points = legacyPolygon.getPoints().stream()
            .map(p -> new VisionProvider.ObjectPolygon.Point(p.getX(), p.getY()))
            .collect(Collectors.toList());
            
        VisionProvider.ObjectPolygon polygon = new VisionProvider.ObjectPolygon(
            legacyPolygon.getLabel(), 
            legacyPolygon.getConfidence(), 
            points
        );
        polygon.setLabelLocalized(legacyPolygon.getLabelLocalized());
        return polygon;
    }
    
    /**
     * Create Vision API client with Firebase credentials
     */
    private ImageAnnotatorClient createVisionClient() throws IOException {
        GoogleCredentials credentials = firebaseCredentialsUtil.getCredentials();
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
            .setCredentialsProvider(() -> credentials)
            .build();
        
        return ImageAnnotatorClient.create(settings);
    }
    
    /**
     * Data class for polygon overlay with points
     */
    public static class OverlayPolygon {
        private String label;
        private String labelLocalized;
        private String labelZh;  // Chinese label
        private float confidence;
        private List<Point> points;
        
        public OverlayPolygon() {}
        
        public OverlayPolygon(String label, float confidence, List<Point> points) {
            this.label = label;
            this.confidence = confidence;
            this.points = points != null ? points : new ArrayList<>();
        }
        
        // Getters and setters
        public String getLabel() { return label; }
        public String getLabelZh() { return labelZh; }
        public void setLabelZh(String labelZh) { this.labelZh = labelZh; }
        public void setLabel(String label) { this.label = label; }
        
        public String getLabelLocalized() { return labelLocalized; }
        public void setLabelLocalized(String labelLocalized) { this.labelLocalized = labelLocalized; }
        
        public float getConfidence() { return confidence; }
        public void setConfidence(float confidence) { this.confidence = confidence; }
        
        public List<Point> getPoints() { return points; }
        public void setPoints(List<Point> points) { this.points = points; }
        
        /**
         * Point with normalized coordinates
         */
        public static class Point {
            private float x;
            private float y;
            
            public Point() {}
            
            public Point(float x, float y) {
                this.x = x;
                this.y = y;
            }
            
            public float getX() { return x; }
            public void setX(float x) { this.x = x; }
            
            public float getY() { return y; }
            public void setY(float y) { this.y = y; }
        }
    }
}