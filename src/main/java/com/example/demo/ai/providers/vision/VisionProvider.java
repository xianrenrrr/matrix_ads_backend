package com.example.demo.ai.providers.vision;

import com.example.demo.ai.core.AIModelProvider;
import com.example.demo.ai.core.AIResponse;
import com.example.demo.model.SceneSegment;
import java.util.List;

/**
 * Interface for computer vision AI providers
 * Handles object detection, segmentation, and scene analysis
 */
public interface VisionProvider extends AIModelProvider {
    
    /**
     * Detect objects and return polygons with Chinese labels
     * @param imageUrl URL of the image to analyze
     * @return AIResponse containing list of detected polygons with labels
     */
    AIResponse<List<ObjectPolygon>> detectObjectPolygons(String imageUrl);
    
    /**
     * Detect scene changes in a video and return segments
     * @param videoUrl URL of the video to analyze
     * @param threshold Scene change threshold (0.0 to 1.0)
     * @return AIResponse containing list of scene segments
     */
    AIResponse<List<SceneSegment>> detectSceneChanges(String videoUrl, double threshold);
    
    /**
     * Extract keyframe from video at specific timestamp
     * @param videoUrl URL of the video
     * @param timestampMs Timestamp in milliseconds
     * @return AIResponse containing keyframe image URL
     */
    AIResponse<String> extractKeyframe(String videoUrl, long timestampMs);
    
    /**
     * Object polygon data structure for vision results
     */
    class ObjectPolygon {
        private String label;           // English label
        private String labelLocalized; // Chinese label
        private float confidence;       // Confidence score 0-1
        private List<Point> points;     // Polygon points
        
        public ObjectPolygon() {}
        
        public ObjectPolygon(String label, float confidence, List<Point> points) {
            this.label = label;
            this.confidence = confidence;
            this.points = points;
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
         * Point in normalized coordinates (0.0 to 1.0)
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