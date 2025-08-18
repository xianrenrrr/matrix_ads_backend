package com.example.demo.ai.services;

import com.example.demo.model.Scene;
import com.example.demo.model.SceneSegment;
import com.example.demo.ai.services.OverlayLegendService;
import com.example.demo.ai.providers.vision.GoogleVisionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles overlay processing - separated concern
 */
public class OverlayProcessor {
    private static final Logger log = LoggerFactory.getLogger(OverlayProcessor.class);
    
    private final GoogleVisionProvider objectService;
    private final OverlayLegendService legendService;
    
    public OverlayProcessor(GoogleVisionProvider objectService, 
                           OverlayLegendService legendService) {
        this.objectService = objectService;
        this.legendService = legendService;
    }
    
    public void processOverlays(Scene scene, SceneSegment segment, String keyframeUrl) {
        // Start with segment overlays
        if (hasOverlayObjects(segment)) {
            applyObjectOverlay(scene, segment);
        } else {
            applyGridOverlay(scene);
        }
        
        // Try polygon detection if we have services and keyframe
        if (shouldDetectPolygons(keyframeUrl)) {
            tryPolygonDetection(scene, keyframeUrl);
        }
        
        // Generate legend if needed
        generateLegendIfNeeded(scene);
    }
    
    private boolean hasOverlayObjects(SceneSegment segment) {
        return segment.getOverlayObjects() != null && !segment.getOverlayObjects().isEmpty();
    }
    
    private void applyObjectOverlay(Scene scene, SceneSegment segment) {
        scene.setOverlayType("objects");
        scene.setOverlayObjects(segment.getOverlayObjects());
        log.info("Scene {}: Applied {} object overlays", 
                scene.getSceneNumber(), segment.getOverlayObjects().size());
    }
    
    private void applyGridOverlay(Scene scene) {
        scene.setOverlayType("grid");
        scene.setScreenGridOverlay(java.util.List.of(5)); // Center grid
        log.info("Scene {}: Applied grid overlay", scene.getSceneNumber());
    }
    
    private boolean shouldDetectPolygons(String keyframeUrl) {
        return objectService != null && keyframeUrl != null && !keyframeUrl.isBlank();
    }
    
    private void tryPolygonDetection(Scene scene, String keyframeUrl) {
        try {
            var polygons = objectService.detectObjectPolygons(keyframeUrl);
            if (!polygons.isEmpty()) {
                scene.setOverlayType("polygons");
                scene.setOverlayPolygons(polygons);
                scene.setOverlayObjects(null); // Clear boxes
                log.info("Scene {}: Detected {} polygons", 
                        scene.getSceneNumber(), polygons.size());
            }
        } catch (Exception e) {
            log.error("Polygon detection failed for scene {}: {}", 
                     scene.getSceneNumber(), e.getMessage());
        }
    }
    
    private void generateLegendIfNeeded(Scene scene) {
        if (legendService == null || "grid".equals(scene.getOverlayType())) {
            return;
        }
        
        var legend = legendService.buildLegend(scene, "zh-CN");
        scene.setLegend(legend);
        
        if (!legend.isEmpty()) {
            log.info("Scene {}: Generated legend with {} items", 
                    scene.getSceneNumber(), legend.size());
        }
    }
}