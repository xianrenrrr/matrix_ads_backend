package com.example.demo.ai.vision;

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
public class ObjectLocalizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectLocalizationService.class);
    
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
    public List<OverlayPolygon> detectObjectPolygons(String imageUrl) {
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