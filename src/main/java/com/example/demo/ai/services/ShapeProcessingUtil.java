package com.example.demo.ai.services;

import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.ai.seg.dto.*;
import com.example.demo.model.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared utility for processing shapes and applying labels to scenes
 * Eliminates code duplication between SceneAnalysisServiceImpl and TemplateAIServiceImpl
 */
@Component
public class ShapeProcessingUtil {
    private static final Logger log = LoggerFactory.getLogger(ShapeProcessingUtil.class);
    
    /**
     * Process detected shapes and add them to the scene with Chinese labels
     * @param scene The scene to populate with overlays
     * @param shapes List of detected shapes (boxes or polygons)
     * @param keyframeUrl URL of the keyframe image
     * @param objectLabelService Service for labeling regions
     * @param language Language for labels (zh-CN, en, etc.)
     * @return true if shapes were successfully processed, false otherwise
     */
    public boolean processShapesWithLabels(
            Scene scene,
            List<OverlayShape> shapes,
            String keyframeUrl,
            ObjectLabelService objectLabelService,
            String language) {
        
        if (shapes == null || shapes.isEmpty()) {
            return false;
        }
        
        try {
            boolean hasPolygons = false;
            boolean hasBoxes = false;
            
            // Build region list for one-shot labeling
            Map<OverlayShape, String> idMap = new HashMap<>();
            List<ObjectLabelService.RegionBox> regions = new ArrayList<>();
            int idCounter = 1;
            
            for (OverlayShape s : shapes) {
                String id = "p" + (idCounter++);
                idMap.put(s, id);
                double x, y, w, h;
                
                if (s instanceof OverlayBox b) {
                    x = clamp01(b.x()); 
                    y = clamp01(b.y()); 
                    w = clamp01(b.w()); 
                    h = clamp01(b.h());
                } else if (s instanceof OverlayPolygon p) {
                    double minX = 1.0, minY = 1.0, maxX = 0.0, maxY = 0.0;
                    for (Point pt : p.points()) {
                        minX = Math.min(minX, pt.x()); 
                        minY = Math.min(minY, pt.y());
                        maxX = Math.max(maxX, pt.x()); 
                        maxY = Math.max(maxY, pt.y());
                    }
                    x = clamp01(minX); 
                    y = clamp01(minY); 
                    w = clamp01(maxX - minX); 
                    h = clamp01(maxY - minY);
                } else { 
                    continue; 
                }
                
                regions.add(new ObjectLabelService.RegionBox(id, x, y, w, h));
            }

            // Get Chinese labels for regions
            Map<String, ObjectLabelService.LabelResult> regionLabels = new HashMap<>();
            try {
                regionLabels = objectLabelService.labelRegions(
                    keyframeUrl, 
                    regions, 
                    language != null ? language : "zh-CN"
                );
            } catch (Exception e) {
                log.warn("Failed to label regions: {}", e.getMessage());
            }

            // Apply labels to shapes
            for (OverlayShape shape : shapes) {
                String labelZh = null;
                String sid = idMap.get(shape);
                
                if (sid != null && regionLabels.containsKey(sid)) {
                    var lr = regionLabels.get(sid);
                    if (lr != null && lr.labelZh != null && !lr.labelZh.isBlank() && lr.conf >= 0.8) {
                        labelZh = lr.labelZh;
                    }
                }
                
                if (labelZh == null) labelZh = "未知";
                
                // Create shapes with Chinese labels
                if (shape instanceof OverlayPolygon polygon) {
                    hasPolygons = true;
                    if (scene.getOverlayPolygons() == null) {
                        scene.setOverlayPolygons(new ArrayList<>());
                    }
                    
                    // Convert to scene polygon
                    com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon scenePolygon = 
                        new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon();
                    scenePolygon.setLabel(polygon.label());
                    scenePolygon.setLabelZh(labelZh);
                    scenePolygon.setLabelLocalized(labelZh);
                    scenePolygon.setConfidence((float) polygon.confidence());
                    
                    // Convert points
                    List<com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point> scenePoints = new ArrayList<>();
                    for (Point p : polygon.points()) {
                        scenePoints.add(new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point(
                            (float) p.x(), (float) p.y()
                        ));
                    }
                    scenePolygon.setPoints(scenePoints);
                    scene.getOverlayPolygons().add(scenePolygon);
                    
                } else if (shape instanceof OverlayBox box) {
                    hasBoxes = true;
                    if (scene.getOverlayObjects() == null) {
                        scene.setOverlayObjects(new ArrayList<>());
                    }
                    
                    Scene.ObjectOverlay overlay = new Scene.ObjectOverlay();
                    overlay.setLabel(box.label());
                    overlay.setLabelZh(labelZh);
                    overlay.setLabelLocalized(labelZh);
                    overlay.setConfidence((float) box.confidence());
                    overlay.setX((float) box.x());
                    overlay.setY((float) box.y());
                    overlay.setWidth((float) box.w());
                    overlay.setHeight((float) box.h());
                    
                    scene.getOverlayObjects().add(overlay);
                }
            }
            
            // Set overlay type priority: polygons > objects > grid
            if (hasPolygons) {
                scene.setOverlayType("polygons");
            } else if (hasBoxes) {
                scene.setOverlayType("objects");
            } else {
                scene.setOverlayType("grid");
            }

            // Set dominant object's Chinese label
            if (!shapes.isEmpty()) {
                OverlayShape dominant = shapes.get(0);
                String dom = null;
                if (dominant instanceof OverlayBox db) dom = db.labelZh();
                else if (dominant instanceof OverlayPolygon dp) dom = dp.labelZh();
                scene.setShortLabelZh(dom);
            }
            
            return hasPolygons || hasBoxes;
            
        } catch (Exception e) {
            log.error("Failed to process shapes: {}", e.getMessage());
            return false;
        }
    }
    
    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
