package com.example.demo.model;

public class LegendItem {
    private String label;          // EN
    private String labelLocalized; // ZH (assume provided by pipeline)
    private float confidence;
    private String colorHex;       // e.g. "#FF3B30"
    
    public LegendItem() {}
    
    public LegendItem(String label, String labelLocalized, float confidence, String colorHex) {
        this.label = label;
        this.labelLocalized = labelLocalized;
        this.confidence = confidence;
        this.colorHex = colorHex;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public String getLabelLocalized() {
        return labelLocalized;
    }
    
    public void setLabelLocalized(String labelLocalized) {
        this.labelLocalized = labelLocalized;
    }
    
    public float getConfidence() {
        return confidence;
    }
    
    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }
    
    public String getColorHex() {
        return colorHex;
    }
    
    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }
}