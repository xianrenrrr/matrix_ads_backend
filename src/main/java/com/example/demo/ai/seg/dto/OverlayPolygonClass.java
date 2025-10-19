package com.example.demo.ai.seg.dto;

import java.util.List;

/**
 * Polygon overlay for scene guidance
 * Moved from GoogleVisionProvider to standalone class
 */
public class OverlayPolygonClass {
    private String label;
    private String labelLocalized;
    private String labelZh;
    private float confidence;
    private List<Point> points;
    
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
    
    public OverlayPolygonClass() {}
    
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    
    public String getLabelLocalized() { return labelLocalized; }
    public void setLabelLocalized(String labelLocalized) { this.labelLocalized = labelLocalized; }
    
    public String getLabelZh() { return labelZh; }
    public void setLabelZh(String labelZh) { this.labelZh = labelZh; }
    
    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }
    
    public List<Point> getPoints() { return points; }
    public void setPoints(List<Point> points) { this.points = points; }
}
