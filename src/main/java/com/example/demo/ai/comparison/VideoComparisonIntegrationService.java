package com.example.demo.ai.comparison;

import com.example.demo.dao.VideoDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Integration service to connect video comparison with existing DAOs
 * This service handles the data retrieval and processing needed for comparisons
 */
@Service
public class VideoComparisonIntegrationService {

    @Autowired
    private VideoDao videoDao;
    
    @Autowired
    private TemplateDao templateDao;

    /**
     * Get processed scenes from a template by ID
     * @param templateId The template ID to look up
     * @return List of scenes with block descriptions ready for comparison
     */
    public List<Map<String, String>> getTemplateScenesById(String templateId) {
        try {
            System.out.printf("Retrieving template scenes for ID: %s%n", templateId);
            
            ManualTemplate template = templateDao.getTemplate(templateId);
            if (template == null) {
                System.err.printf("Template not found: %s%n", templateId);
                return new ArrayList<>();
            }
            
            System.out.printf("Found template: %s%n", template.getTemplateTitle());
            return extractScenesFromTemplate(template);
            
        } catch (ExecutionException | InterruptedException e) {
            System.err.printf("Error retrieving template %s: %s%n", templateId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get processed scenes from a user video by ID
     * This assumes the video has been processed by Task 1 AI Template Generator
     */
    public List<Map<String, String>> getUserVideoScenesById(String videoId) {
        try {
            System.out.printf("Retrieving user video scenes for ID: %s%n", videoId);
            
            Video video = videoDao.getVideoById(videoId);
            if (video == null) {
                System.err.printf("Video not found: %s%n", videoId);
                return new ArrayList<>();
            }
            
            System.out.printf("Found video: %s%n", video.getTitle());
            
            // Check if video has an associated AI-generated template from Task 1
            if (video.getTemplateId() != null && !video.getTemplateId().isEmpty()) {
                System.out.printf("Video has associated template ID: %s%n", video.getTemplateId());
                return getTemplateScenesById(video.getTemplateId());
            }
            
            // If no template ID, look for AI-generated scenes stored directly on the video
            // This would be populated by Task 1 AI Template Generator
            if (video.getAiGeneratedScenes() != null && !video.getAiGeneratedScenes().isEmpty()) {
                System.out.printf("Using AI-generated scenes stored on video%n");
                List<Map<String, String>> scenes = new ArrayList<>();
                for (Map<String, Object> sceneData : video.getAiGeneratedScenes()) {
                    Map<String, String> scene = new HashMap<>();
                    for (Map.Entry<String, Object> entry : sceneData.entrySet()) {
                        scene.put(entry.getKey(), entry.getValue().toString());
                    }
                    scenes.add(scene);
                }
                return scenes;
            }
            
            System.out.printf("No processed scenes found for video %s, returning empty list%n", videoId);
            return new ArrayList<>();
            
        } catch (ExecutionException | InterruptedException e) {
            System.err.printf("Error retrieving video %s: %s%n", videoId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Extract scene block descriptions from a ManualTemplate
     * This converts the template structure to the format needed for comparison
     */
    public List<Map<String, String>> extractScenesFromTemplate(ManualTemplate template) {
        List<Map<String, String>> scenes = new ArrayList<>();
        
        if (template.getScenes() != null) {
            for (Scene scene : template.getScenes()) {
                Map<String, String> sceneDescriptions = new HashMap<>();
                
                // Use block descriptions if available (from Task 1 AI processing)
                if (scene.getBlockDescriptions() != null && !scene.getBlockDescriptions().isEmpty()) {
                    sceneDescriptions.putAll(scene.getBlockDescriptions());
                } else {
                    // Fallback: Use script line for all blocks if block descriptions not available
                    String fallbackDescription = scene.getScriptLine() != null ? 
                        scene.getScriptLine() : "No description available";
                    
                    // Fill all 9 blocks with the same description as fallback
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                            sceneDescriptions.put(row + "_" + col, fallbackDescription);
                        }
                    }
                }
                
                scenes.add(sceneDescriptions);
            }
        }
        
        return scenes;
    }

    /**
     * Example template scenes for testing
     * Remove this once you have real template integration
     */
    private List<Map<String, String>> createExampleTemplateScenes() {
        List<Map<String, String>> scenes = new ArrayList<>();
        
        Map<String, String> scene1 = new HashMap<>();
        scene1.put("0_0", "Clean white background with professional lighting");
        scene1.put("0_1", "Professional woman in business attire presenting");
        scene1.put("0_2", "Company logo prominently displayed");
        scene1.put("1_0", "Modern desk with laptop showing data");
        scene1.put("1_1", "Woman engaging directly with camera");
        scene1.put("1_2", "Product prominently featured");
        scene1.put("2_0", "Marketing materials neatly arranged");
        scene1.put("2_1", "Professional hand gestures while speaking");
        scene1.put("2_2", "Clear call-to-action text overlay");
        scenes.add(scene1);
        
        return scenes;
    }

    /**
     * Example user video scenes for testing
     * Remove this once you have real video processing integration
     */
    private List<Map<String, String>> createExampleUserScenes() {
        List<Map<String, String>> scenes = new ArrayList<>();
        
        Map<String, String> scene1 = new HashMap<>();
        scene1.put("0_0", "Light grey background with natural lighting");
        scene1.put("0_1", "Young man in casual shirt speaking confidently");
        scene1.put("0_2", "Brand logo visible but smaller");
        scene1.put("1_0", "Desk with smartphone displaying information");
        scene1.put("1_1", "Man looking directly at camera while talking");
        scene1.put("1_2", "Product visible but not centered");
        scene1.put("2_0", "Some papers scattered on desk");
        scene1.put("2_1", "Casual hand movements while explaining");
        scene1.put("2_2", "Text overlay with similar message");
        scenes.add(scene1);
        
        return scenes;
    }
}