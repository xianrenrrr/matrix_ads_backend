package com.example.demo.ai.services;

import com.example.demo.model.Scene;
import com.example.demo.model.SceneSegment;
import com.example.demo.ai.services.OverlayLegendService;
import com.example.demo.ai.services.AIOrchestrator;
import com.example.demo.ai.core.AIModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles overlay processing - separated concern
 */
public class OverlayProcessor {
    private static final Logger log = LoggerFactory.getLogger(OverlayProcessor.class);
    
    private final AIOrchestrator aiOrchestrator;
    private final OverlayLegendService legendService;
    private final String language;
    
    public OverlayProcessor(AIOrchestrator aiOrchestrator, 
                           OverlayLegendService legendService) {
        this(aiOrchestrator, legendService, "zh-CN");
    }
    
    public OverlayProcessor(AIOrchestrator aiOrchestrator, 
                           OverlayLegendService legendService,
                           String language) {
        this.aiOrchestrator = aiOrchestrator;
        this.legendService = legendService;
        this.language = language;
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
        return aiOrchestrator != null && keyframeUrl != null && !keyframeUrl.isBlank();
    }
    
    private void tryPolygonDetection(Scene scene, String keyframeUrl) {
        try {
            // Use AI orchestrator to get the best available vision provider (YOLO -> Google Vision)
            var response = aiOrchestrator.<java.util.List<com.example.demo.ai.providers.vision.VisionProvider.ObjectPolygon>>executeWithFallback(
                AIModelType.VISION, 
                "detectObjectPolygons",
                provider -> {
                    var visionProvider = (com.example.demo.ai.providers.vision.VisionProvider) provider;
                    return visionProvider.detectObjectPolygons(keyframeUrl);
                }
            );
            
            if (response.isSuccess() && response.getData() != null) {
                var visionPolygons = response.getData();
                
                if (!visionPolygons.isEmpty()) {
                    // Convert VisionProvider.ObjectPolygon to GoogleVisionProvider.OverlayPolygon for compatibility
                    var legacyPolygons = visionPolygons.stream()
                        .map(this::convertToLegacyPolygon)
                        .collect(java.util.stream.Collectors.toList());
                    
                    scene.setOverlayType("polygons");
                    scene.setOverlayPolygons(legacyPolygons);
                    scene.setOverlayObjects(null); // Clear boxes
                    log.info("Scene {}: Detected {} polygons using {} (via AI orchestrator)", 
                            scene.getSceneNumber(), visionPolygons.size(), response.getModelUsed());
                }
            }
        } catch (Exception e) {
            log.error("Polygon detection failed for scene {}: {}", 
                     scene.getSceneNumber(), e.getMessage());
        }
    }
    
    /**
     * Convert VisionProvider.ObjectPolygon to GoogleVisionProvider.OverlayPolygon for compatibility
     */
    private com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon convertToLegacyPolygon(
            com.example.demo.ai.providers.vision.VisionProvider.ObjectPolygon visionPolygon) {
        
        var legacyPoints = visionPolygon.getPoints().stream()
            .map(p -> new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point(p.getX(), p.getY()))
            .collect(java.util.stream.Collectors.toList());
            
        var legacyPolygon = new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon(
            visionPolygon.getLabel(), visionPolygon.getConfidence(), legacyPoints);
        legacyPolygon.setLabelLocalized(visionPolygon.getLabelLocalized());
        return legacyPolygon;
    }
    
    
    private void generateLegendIfNeeded(Scene scene) {
        if (legendService == null || "grid".equals(scene.getOverlayType())) {
            return;
        }
        
        var legend = legendService.buildLegend(scene, language);
        scene.setLegend(legend);
        
        if (!legend.isEmpty()) {
            log.info("Scene {}: Generated legend with {} items", 
                    scene.getSceneNumber(), legend.size());
        }
    }
}