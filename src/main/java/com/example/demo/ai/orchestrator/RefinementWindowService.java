package com.example.demo.ai.orchestrator;

import com.example.demo.model.SceneSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for creating refinement windows around scenes that need better object detection.
 * Windows include time padding to capture more context around the original scene boundaries.
 */
@Service
public class RefinementWindowService {
    
    @Value("${ai.vi.refine.timePaddingMs:150}")
    private long timePaddingMs;
    
    /**
     * Creates refinement windows for scenes that need better object detection.
     * Each window includes the original scene time plus padding on both sides.
     * 
     * @param scenesNeedingRefinement List of scenes that need refinement
     * @param allScenes All scenes (for boundary checking)
     * @return List of segment windows for targeted analysis
     */
    public List<SegmentWindow> createRefinementWindows(List<SceneSegment> scenesNeedingRefinement, 
                                                       List<SceneSegment> allScenes) {
        List<SegmentWindow> windows = new ArrayList<>();
        Duration padding = Duration.ofMillis(timePaddingMs);
        
        System.out.printf("RefinementWindowService: Creating windows for %d scenes with %dms padding%n", 
                         scenesNeedingRefinement.size(), timePaddingMs);
        
        for (SceneSegment scene : scenesNeedingRefinement) {
            int sceneIndex = findSceneIndex(scene, allScenes);
            
            if (sceneIndex == -1) {
                System.out.printf("RefinementWindowService: Warning - couldn't find scene in list, skipping%n");
                continue;
            }
            
            Duration originalStart = scene.getStartTime();
            Duration originalEnd = scene.getEndTime();
            
            if (originalStart == null || originalEnd == null) {
                System.out.printf("RefinementWindowService: Warning - scene %d has null timing, skipping%n", sceneIndex);
                continue;
            }
            
            // Apply padding
            Duration windowStart = originalStart.minus(padding);
            Duration windowEnd = originalEnd.plus(padding);
            
            // Clamp to video boundaries (ensure non-negative start and don't extend past video end)
            if (windowStart.isNegative()) {
                windowStart = Duration.ZERO;
            }
            
            // Find video end time from last scene
            Duration videoEndTime = findVideoEndTime(allScenes);
            if (videoEndTime != null && windowEnd.compareTo(videoEndTime) > 0) {
                windowEnd = videoEndTime;
            }
            
            String reason = determineRefinementReason(scene);
            
            SegmentWindow window = new SegmentWindow(windowStart, windowEnd, sceneIndex, reason);
            windows.add(window);
            
            System.out.printf("RefinementWindowService: Created window %s%n", window);
        }
        
        return windows;
    }
    
    private int findSceneIndex(SceneSegment targetScene, List<SceneSegment> allScenes) {
        for (int i = 0; i < allScenes.size(); i++) {
            SceneSegment scene = allScenes.get(i);
            
            // Match by timing (most reliable identifier)
            if (scene.getStartTime() != null && targetScene.getStartTime() != null &&
                scene.getStartTime().equals(targetScene.getStartTime()) &&
                scene.getEndTime() != null && targetScene.getEndTime() != null &&
                scene.getEndTime().equals(targetScene.getEndTime())) {
                return i;
            }
        }
        return -1;
    }
    
    private Duration findVideoEndTime(List<SceneSegment> allScenes) {
        if (allScenes.isEmpty()) {
            return null;
        }
        
        Duration maxEndTime = Duration.ZERO;
        for (SceneSegment scene : allScenes) {
            if (scene.getEndTime() != null && scene.getEndTime().compareTo(maxEndTime) > 0) {
                maxEndTime = scene.getEndTime();
            }
        }
        
        return maxEndTime.equals(Duration.ZERO) ? null : maxEndTime;
    }
    
    private String determineRefinementReason(SceneSegment scene) {
        List<com.example.demo.model.Scene.ObjectOverlay> overlays = scene.getOverlayObjects();
        
        if (overlays == null || overlays.isEmpty()) {
            return "no_objects_detected";
        }
        
        // Calculate average confidence
        float totalConfidence = 0;
        for (com.example.demo.model.Scene.ObjectOverlay overlay : overlays) {
            totalConfidence += overlay.getConfidence();
        }
        float avgConfidence = totalConfidence / overlays.size();
        
        if (avgConfidence < 0.6) {
            return String.format("low_avg_confidence_%.2f", avgConfidence);
        }
        
        // Check for minimum area
        boolean hasMinArea = false;
        for (com.example.demo.model.Scene.ObjectOverlay overlay : overlays) {
            float area = overlay.getWidth() * overlay.getHeight();
            if (area >= 0.02) {
                hasMinArea = true;
                break;
            }
        }
        
        if (!hasMinArea) {
            return "objects_too_small";
        }
        
        return "quality_gate_failed";
    }
}