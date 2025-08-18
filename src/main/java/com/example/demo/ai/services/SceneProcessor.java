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
        
        // Set defaults - no conditionals
        setDefaults(scene, segment, language);
        
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
        // Set language-specific defaults
        scene.setSourceAspect("9:16");
        
        if ("zh".equals(language) || "zh-CN".equals(language)) {
            // Chinese defaults
            scene.setDeviceOrientation("手机（竖屏 9:16）");
            scene.setPersonPosition(segment.isPersonPresent() ? "居中" : "无偏好");
            scene.setPreferredGender("无偏好");
            scene.setMovementInstructions("静止");
            scene.setBackgroundInstructions("使用与示例画面相似的背景");
            scene.setSpecificCameraInstructions("按照示例中显示的构图拍摄");
            scene.setAudioNotes("说话清楚，配合场景的语调");
        } else {
            // English defaults
            scene.setDeviceOrientation("Phone (Portrait 9:16)");
            scene.setPersonPosition(segment.isPersonPresent() ? "Center" : "No Preference");
            scene.setPreferredGender("No Preference");
            scene.setMovementInstructions("Static");
            scene.setBackgroundInstructions("Use similar background as shown in example frame");
            scene.setSpecificCameraInstructions("Follow the framing shown in the example");
            scene.setAudioNotes("Clear speech, match the tone of the scene");
        }
    }
}