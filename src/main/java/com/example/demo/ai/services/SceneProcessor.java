package com.example.demo.ai.services;

import com.example.demo.model.Scene;
import com.example.demo.model.SceneSegment;
import java.time.Duration;

/**
 * Simplified scene processor - GOOD TASTE version
 * No nested conditionals, no special cases
 */
public class SceneProcessor {
    
    public static Scene createFromSegment(SceneSegment segment, int sceneNumber) {
        return createFromSegment(segment, sceneNumber, "zh-CN"); // Default to Chinese
    }
    
    public static Scene createFromSegment(SceneSegment segment, int sceneNumber, String language) {
        Scene scene = new Scene();
        scene.setSceneNumber(sceneNumber);
        
        // Set scene title based on language
        if ("zh".equals(language) || "zh-CN".equals(language)) {
            scene.setSceneTitle("场景 " + sceneNumber);
        } else {
            scene.setSceneTitle("Scene " + sceneNumber);
        }
        
        scene.setSceneSource("ai");
        
        // Time calculation - simple and direct
        Duration duration = calculateDuration(segment);
        scene.setStartTime(segment.getStartTime());
        scene.setEndTime(segment.getEndTime());
        scene.setSceneDurationInSeconds(duration.getSeconds());
        
        // Labels and person detection
        scene.setPresenceOfPerson(segment.isPersonPresent());
        scene.setScriptLine(formatLabels(segment));
        
        // Do not apply guidance presets here; guidance is AI-driven later.
        // Device orientation will be derived from keyframe image (not preset).
        
        return scene;
    }
    
    private static Duration calculateDuration(SceneSegment segment) {
        if (segment.getStartTime() == null || segment.getEndTime() == null) {
            return Duration.ZERO;
        }
        Duration d = segment.getEndTime().minus(segment.getStartTime());
        return d.isNegative() ? Duration.ZERO : d;
    }
    
    private static String formatLabels(SceneSegment segment) {
        if (segment.getLabels() == null || segment.getLabels().isEmpty()) {
            return "";
        }
        return String.join(", ", segment.getLabels());
    }
    
    private static void setDefaults(Scene scene, SceneSegment segment, String language) {
        // Intentionally left blank: no default guidance presets.
    }
}
