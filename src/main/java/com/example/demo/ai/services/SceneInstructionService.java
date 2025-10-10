package com.example.demo.ai.services;

import com.example.demo.model.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for generating AI-powered scene instructions
 */
@Service
public class SceneInstructionService {
    private static final Logger log = LoggerFactory.getLogger(SceneInstructionService.class);
    
    /**
     * Generate filming instructions based on scene analysis
     * @param scene The scene with detected objects/shapes
     * @param sceneDescription User-provided scene description
     * @param language Language for instructions (zh-CN, en, etc.)
     */
    public void generateInstructions(Scene scene, String sceneDescription, String language) {
        boolean isChinese = language != null && language.startsWith("zh");
        
        // Generate background instructions based on detected objects
        String backgroundInstructions = generateBackgroundInstructions(scene, sceneDescription, isChinese);
        scene.setBackgroundInstructions(backgroundInstructions);
        
        // Generate camera instructions
        String cameraInstructions = generateCameraInstructions(scene, isChinese);
        scene.setSpecificCameraInstructions(cameraInstructions);
        
        // Generate movement instructions
        String movementInstructions = generateMovementInstructions(scene, isChinese);
        scene.setMovementInstructions(movementInstructions);
        
        log.info("Generated AI instructions for scene with overlay type: {}", scene.getOverlayType());
    }
    
    private String generateBackgroundInstructions(Scene scene, String sceneDescription, boolean isChinese) {
        StringBuilder instructions = new StringBuilder();
        
        if (sceneDescription != null && !sceneDescription.isEmpty()) {
            if (isChinese) {
                instructions.append("场景要求: ").append(sceneDescription).append("。");
            } else {
                instructions.append("Scene requirements: ").append(sceneDescription).append(". ");
            }
        }
        
        // Add instructions based on detected objects
        if ("objects".equals(scene.getOverlayType()) && scene.getOverlayObjects() != null) {
            List<Scene.ObjectOverlay> objects = scene.getOverlayObjects();
            if (!objects.isEmpty()) {
                if (isChinese) {
                    instructions.append("确保画面中包含: ");
                    for (int i = 0; i < objects.size(); i++) {
                        if (i > 0) instructions.append("、");
                        String label = objects.get(i).getLabelZh();
                        instructions.append(label != null ? label : objects.get(i).getLabel());
                    }
                    instructions.append("。");
                } else {
                    instructions.append("Ensure the frame includes: ");
                    for (int i = 0; i < objects.size(); i++) {
                        if (i > 0) instructions.append(", ");
                        instructions.append(objects.get(i).getLabel());
                    }
                    instructions.append(".");
                }
            }
        } else if ("polygons".equals(scene.getOverlayType()) && scene.getOverlayPolygons() != null) {
            if (isChinese) {
                instructions.append("保持画面中关键区域清晰可见。");
            } else {
                instructions.append("Keep key regions in the frame clearly visible.");
            }
        }
        
        if (instructions.length() == 0) {
            return isChinese ? "保持背景简洁，突出主要内容" : "Keep background simple, highlight main content";
        }
        
        return instructions.toString();
    }
    
    private String generateCameraInstructions(Scene scene, boolean isChinese) {
        // Analyze scene composition
        boolean hasMultipleObjects = false;
        if ("objects".equals(scene.getOverlayType()) && scene.getOverlayObjects() != null) {
            hasMultipleObjects = scene.getOverlayObjects().size() > 1;
        }
        
        if (hasMultipleObjects) {
            return isChinese 
                ? "使用中景或全景拍摄，确保所有重要元素都在画面中" 
                : "Use medium or wide shot to ensure all important elements are in frame";
        } else {
            return isChinese 
                ? "保持相机稳定，使用适当的景别突出主体" 
                : "Keep camera stable, use appropriate framing to highlight the subject";
        }
    }
    
    private String generateMovementInstructions(Scene scene, boolean isChinese) {
        // Check if scene has person detection
        if (scene.isPresenceOfPerson()) {
            return isChinese 
                ? "人物动作要自然流畅，与场景描述相符" 
                : "Person's movements should be natural and match scene description";
        }
        
        // For object-focused scenes
        if ("objects".equals(scene.getOverlayType()) || "polygons".equals(scene.getOverlayType())) {
            return isChinese 
                ? "保持画面稳定，避免不必要的晃动" 
                : "Keep frame stable, avoid unnecessary camera shake";
        }
        
        return isChinese 
            ? "根据场景需求调整拍摄角度和运动" 
            : "Adjust shooting angle and movement based on scene requirements";
    }
}
