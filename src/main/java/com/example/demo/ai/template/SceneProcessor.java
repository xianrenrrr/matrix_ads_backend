package com.example.demo.ai.template;

import com.example.demo.model.Scene;
import com.example.demo.model.SceneSegment;
import java.time.Duration;

/**
 * Simplified scene processor - GOOD TASTE version
 * No nested conditionals, no special cases
 */
public class SceneProcessor {
    
    public static Scene createFromSegment(SceneSegment segment, int sceneNumber) {
        Scene scene = new Scene();
        scene.setSceneNumber(sceneNumber);
        scene.setSceneTitle("Scene " + sceneNumber);
        scene.setSceneSource("ai");
        
        // Time calculation - simple and direct
        Duration duration = calculateDuration(segment);
        scene.setStartTime(segment.getStartTime());
        scene.setEndTime(segment.getEndTime());
        scene.setSceneDurationInSeconds(duration.getSeconds());
        
        // Labels and person detection
        scene.setPresenceOfPerson(segment.isPersonPresent());
        scene.setScriptLine(formatLabels(segment));
        
        // Set defaults - no conditionals
        setDefaults(scene, segment);
        
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
    
    private static void setDefaults(Scene scene, SceneSegment segment) {
        // Always the same defaults - no special cases
        scene.setSourceAspect("9:16");
        scene.setDeviceOrientation("Phone (Portrait 9:16)");
        scene.setPersonPosition(segment.isPersonPresent() ? "Center" : "No Preference");
        scene.setPreferredGender("No Preference");
        scene.setMovementInstructions("Static");
        scene.setBackgroundInstructions("Use similar background as shown in example frame");
        scene.setSpecificCameraInstructions("Follow the framing shown in the example");
        scene.setAudioNotes("Clear speech, match the tone of the scene");
    }
}