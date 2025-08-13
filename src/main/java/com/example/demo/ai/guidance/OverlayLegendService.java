package com.example.demo.ai.guidance;

import com.example.demo.model.Scene;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OverlayLegendService {
    
    /**
     * Color palette for overlays - first color is RED as per specs
     */
    private static final String[] COLOR_PALETTE = {
        "#FF3B30", // Red - FIRST overlay must be this color
        "#0A84FF", // Blue
        "#34C759", // Green  
        "#FF9F0A", // Orange
        "#AF52DE", // Purple
        "#32ADE6", // Light Blue
        "#FF375F"  // Pink
    };
    
    /**
     * Build legend for a scene with stable color ordering
     * @param scene Scene with overlay data
     * @param locale Target locale for labels (e.g., "zh-CN")
     * @return List of legend items in same order as overlays are drawn
     */
    public List<LegendItem> buildLegend(Scene scene, String locale) {
        List<LegendItem> legend = new ArrayList<>();
        
        if (scene == null) {
            return legend;
        }
        
        String overlayType = scene.getOverlayType();
        if (overlayType == null) {
            return legend;
        }
        
        switch (overlayType) {
            case "polygons":
                legend = buildPolygonLegend(scene, locale);
                break;
            case "objects":
                legend = buildObjectLegend(scene, locale);
                break;
            case "grid":
            default:
                // Grid mode doesn't have a legend
                break;
        }
        
        return legend;
    }
    
    /**
     * Build legend from polygon overlays
     */
    private List<LegendItem> buildPolygonLegend(Scene scene, String locale) {
        List<LegendItem> legend = new ArrayList<>();
        
        var polygons = scene.getOverlayPolygons();
        if (polygons == null || polygons.isEmpty()) {
            return legend;
        }
        
        for (int i = 0; i < polygons.size(); i++) {
            var polygon = polygons.get(i);
            String colorHex = getColorForIndex(i);
            
            // Prefer localized label, fallback to English
            String displayLabel = polygon.getLabelLocalized() != null ? 
                polygon.getLabelLocalized() : polygon.getLabel();
            
            LegendItem item = new LegendItem(
                displayLabel,
                polygon.getLabel(),
                polygon.getConfidence(),
                colorHex
            );
            
            legend.add(item);
        }
        
        return legend;
    }
    
    /**
     * Build legend from object overlays
     */
    private List<LegendItem> buildObjectLegend(Scene scene, String locale) {
        List<LegendItem> legend = new ArrayList<>();
        
        var objects = scene.getOverlayObjects();
        if (objects == null || objects.isEmpty()) {
            return legend;
        }
        
        for (int i = 0; i < objects.size(); i++) {
            var object = objects.get(i);
            String colorHex = getColorForIndex(i);
            
            // Prefer localized label, fallback to English
            String displayLabel = object.getLabelLocalized() != null ? 
                object.getLabelLocalized() : object.getLabel();
            
            LegendItem item = new LegendItem(
                displayLabel,
                object.getLabel(),
                object.getConfidence(),
                colorHex
            );
            
            legend.add(item);
        }
        
        return legend;
    }
    
    /**
     * Get color for overlay index (0-based)
     * First overlay (index 0) is always RED (#FF3B30)
     */
    private String getColorForIndex(int index) {
        if (index < 0) {
            return COLOR_PALETTE[0]; // Default to red
        }
        
        // Cycle through colors if we have more overlays than colors
        return COLOR_PALETTE[index % COLOR_PALETTE.length];
    }
    
    /**
     * Get the complete color palette
     */
    public String[] getColorPalette() {
        return COLOR_PALETTE.clone();
    }
    
    /**
     * Data class for legend items
     */
    public static class LegendItem {
        private String labelLocalized;  // Localized label to display
        private String label;           // Original English label
        private float confidence;       // AI confidence score (0-1)
        private String colorHex;        // Hex color code
        
        public LegendItem() {}
        
        public LegendItem(String labelLocalized, String label, float confidence, String colorHex) {
            this.labelLocalized = labelLocalized;
            this.label = label;
            this.confidence = confidence;
            this.colorHex = colorHex;
        }
        
        // Getters and setters
        public String getLabelLocalized() { return labelLocalized; }
        public void setLabelLocalized(String labelLocalized) { this.labelLocalized = labelLocalized; }
        
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        
        public float getConfidence() { return confidence; }
        public void setConfidence(float confidence) { this.confidence = confidence; }
        
        public String getColorHex() { return colorHex; }
        public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    }
}package com.example.demo.ai.guidance;

import com.example.demo.model.LegendItem;
import com.example.demo.model.Scene;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OverlayLegendService {
    
    private static final String[] COLORS = {
        "#FF3B30", "#0A84FF", "#34C759", "#FF9F0A", "#AF52DE",
        "#32ADE6", "#FF375F", "#5E5CE6", "#30D158", "#FFD60A"
    };

    public List<LegendItem> buildLegendFromObjects(Scene scene) {
        // scene.getOverlayObjects() is expected to exist
        var objs = Optional.ofNullable(scene.getOverlayObjects()).orElse(List.of());

        // Sort deterministically: confidence * area (w*h), desc
        var sorted = new ArrayList<>(objs);
        sorted.sort((a, b) -> Float.compare(
            (b.getConfidence() * Math.max(0f, b.getWidth()) * Math.max(0f, b.getHeight())),
            (a.getConfidence() * Math.max(0f, a.getWidth()) * Math.max(0f, a.getHeight()))
        ));

        var legend = new ArrayList<LegendItem>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            var o = sorted.get(i);
            var li = new LegendItem();
            li.setLabel(o.getLabel());
            li.setLabelLocalized(o.getLabelLocalized() != null ? o.getLabelLocalized() : o.getLabel());
            li.setConfidence(o.getConfidence());
            li.setColorHex(COLORS[i % COLORS.length]);
            legend.add(li);
        }
        
        // Reorder scene.overlayObjects to match legend order
        scene.setOverlayObjects(sorted);
        return legend;
    }
}