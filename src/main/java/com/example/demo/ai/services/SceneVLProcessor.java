package com.example.demo.ai.services;

import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.model.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared service for processing VL analysis results and converting to Scene overlays.
 * Eliminates code duplication between TemplateAIServiceImpl and SceneAnalysisServiceImpl.
 */
@Service
public class SceneVLProcessor {
    private static final Logger log = LoggerFactory.getLogger(SceneVLProcessor.class);
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    @Autowired
    private OverlayLegendService overlayLegendService;
    
    @Value("${ai.overlay.includeLegend:false}")
    private boolean includeLegend;
    
    /**
     * Analyze video and populate scene with VL results
     * 
     * @param scene Scene to populate
     * @param videoUrl Video URL to analyze
     * @param language Language for analysis
     * @return true if analysis succeeded, false otherwise
     */
    public boolean analyzeAndPopulateScene(Scene scene, String videoUrl, String language) {
        return analyzeAndPopulateScene(scene, videoUrl, language, null);
    }
    
    /**
     * Analyze video and populate scene with VL results (with optional user context)
     * 
     * @param scene Scene to populate
     * @param videoUrl Video URL to analyze
     * @param language Language for analysis
     * @param userDescription Optional user-provided description for context
     * @return true if analysis succeeded, false otherwise
     */
    public boolean analyzeAndPopulateScene(Scene scene, String videoUrl, String language, String userDescription) {
        try {
            log.info("=== UNIFIED VIDEO ANALYSIS ===");
            if (userDescription != null && !userDescription.isEmpty()) {
                log.info("User context: {}", userDescription);
            }
            ObjectLabelService.VideoAnalysisResult vlResult = objectLabelService.analyzeSceneVideo(videoUrl, language, userDescription);
            
            if (vlResult != null) {
                log.info("✅ Video analysis successful: {} objects detected", 
                        vlResult.objects != null ? vlResult.objects.size() : 0);
                
                // Save complete VL response (ALWAYS save raw output for reasoning model)
                scene.setVlRawResponse(vlResult.rawVLResponse);
                scene.setSceneDescriptionFromVL(vlResult.sceneDescription);
                scene.setDominantAction(vlResult.dominantAction);
                scene.setAudioContext(vlResult.audioContext);
                
                log.info("✅ VL raw response saved ({} chars)", 
                        vlResult.rawVLResponse != null ? vlResult.rawVLResponse.length() : 0);
                
                // Convert detected objects to scene overlays (if any)
                if (vlResult.objects != null && !vlResult.objects.isEmpty()) {
                    convertVLObjectsToSceneOverlays(scene, vlResult, language);
                    return true;
                } else {
                    // No structured objects, but we have the description - use grid for now
                    log.info("No structured objects detected, using grid overlay (raw VL text saved for reasoning)");
                    scene.setOverlayType("grid");
                    scene.setScreenGridOverlay(List.of(5));
                    return true;  // Still success - we have the VL description!
                }
            } else {
                log.warn("❌ Video analysis returned null");
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Video analysis failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Convert VL analysis objects to Scene overlays
     */
    private void convertVLObjectsToSceneOverlays(
            Scene scene, 
            ObjectLabelService.VideoAnalysisResult vlResult, 
            String language) {
        
        List<Scene.ObjectOverlay> overlays = new ArrayList<>();
        Map<String, String> motionMap = new HashMap<>();
        
        for (ObjectLabelService.VideoAnalysisResult.DetectedObject obj : vlResult.objects) {
            Scene.ObjectOverlay overlay = new Scene.ObjectOverlay();
            overlay.setLabel(obj.labelEn != null ? obj.labelEn : obj.labelZh);
            overlay.setLabelZh(obj.labelZh);
            overlay.setLabelLocalized(obj.labelZh);
            overlay.setConfidence((float) obj.confidence);
            
            if (obj.boundingBox != null) {
                overlay.setX((float) obj.boundingBox.x);
                overlay.setY((float) obj.boundingBox.y);
                overlay.setWidth((float) obj.boundingBox.w);
                overlay.setHeight((float) obj.boundingBox.h);
            }
            
            overlays.add(overlay);
            
            // Store motion description
            if (obj.motionDescription != null && !obj.motionDescription.isBlank()) {
                motionMap.put(obj.id, obj.motionDescription);
            }
        }
        
        scene.setOverlayObjects(overlays);
        scene.setOverlayType("objects");
        scene.setObjectMotion(motionMap);
        
        // Set dominant object as short label
        if (!vlResult.objects.isEmpty()) {
            scene.setShortLabelZh(vlResult.objects.get(0).labelZh);
        }
        
        // Build legend
        if (includeLegend && overlayLegendService != null) {
            var legend = overlayLegendService.buildLegend(scene, language != null ? language : "zh-CN");
            scene.setLegend(legend);
        }
    }
}
