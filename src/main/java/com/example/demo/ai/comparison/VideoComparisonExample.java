package com.example.demo.ai.comparison;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example usage of the Video Comparison services
 * This shows how to integrate Task 2 comparison with Task 1 template generation
 */
@Component
public class VideoComparisonExample {

    @Autowired
    private VideoComparisonService videoComparisonService;

    /**
     * Example: Compare a user video against a reference template
     */
    public void exampleVideoComparison() {
        
        // Example template scenes (from Task 1 AI Template Generator)
        List<Map<String, String>> templateScenes = createExampleTemplateScenes();
        
        // Example user video scenes (also from Task 1 processing)
        List<Map<String, String>> userScenes = createExampleUserScenes();
        
        // Perform the comparison
        ComparisonResult result = videoComparisonService.compareVideoToTemplate(
            templateScenes, userScenes
        );
        
        // Print results
        System.out.println("=== COMPARISON RESULTS ===");
        System.out.printf("Overall Similarity: %.1f%%\n", result.getOverallScore() * 100);
        
        for (SceneComparison scene : result.getSceneComparisons()) {
            System.out.printf("Scene %d: %.1f%% similarity\n", 
                             scene.getSceneIndex() + 1, scene.getSimilarity() * 100);
            
            // Show block scores
            for (Map.Entry<String, Double> block : scene.getBlockScores().entrySet()) {
                System.out.printf("  Block %s: %.1f%%\n", 
                                 block.getKey(), block.getValue() * 100);
            }
        }
        
        // Generate detailed report
        String report = videoComparisonService.generateComparisonReport(result);
        System.out.println("\n" + report);
    }
    
    private List<Map<String, String>> createExampleTemplateScenes() {
        List<Map<String, String>> scenes = new ArrayList<>();
        
        // Scene 1: Marketing professional setup
        Map<String, String> scene1 = new HashMap<>();
        scene1.put("0_0", "Clean white background with minimal lighting");
        scene1.put("0_1", "Professional woman in business attire smiling");
        scene1.put("0_2", "Company logo visible in corner");
        scene1.put("1_0", "Desk with laptop showing graphs");
        scene1.put("1_1", "Woman pointing at camera engaging audience");
        scene1.put("1_2", "Product package clearly displayed");
        scene1.put("2_0", "Marketing materials spread on desk");
        scene1.put("2_1", "Woman's hands gesturing enthusiastically");
        scene1.put("2_2", "Call-to-action text overlay");
        scenes.add(scene1);
        
        // Scene 2: Product demonstration
        Map<String, String> scene2 = new HashMap<>();
        scene2.put("0_0", "Modern kitchen setting with good lighting");
        scene2.put("0_1", "Product being used by person");
        scene2.put("0_2", "Close-up of product features");
        scene2.put("1_0", "Person demonstrating product benefits");
        scene2.put("1_1", "Product in action showing results");
        scene2.put("1_2", "Before and after comparison");
        scene2.put("2_0", "Happy customer using product");
        scene2.put("2_1", "Product packaging and branding");
        scene2.put("2_2", "Purchase information and pricing");
        scenes.add(scene2);
        
        return scenes;
    }
    
    private List<Map<String, String>> createExampleUserScenes() {
        List<Map<String, String>> scenes = new ArrayList<>();
        
        // User Scene 1: Similar but not identical
        Map<String, String> scene1 = new HashMap<>();
        scene1.put("0_0", "Grey background with natural lighting");
        scene1.put("0_1", "Young man in casual shirt speaking");
        scene1.put("0_2", "Brand logo partially visible");
        scene1.put("1_0", "Desk with smartphone showing data");
        scene1.put("1_1", "Man looking directly at camera");
        scene1.put("1_2", "Product box somewhat visible");
        scene1.put("2_0", "Papers scattered on desk surface");
        scene1.put("2_1", "Man's hands moving while talking");
        scene1.put("2_2", "Text overlay with different message");
        scenes.add(scene1);
        
        // User Scene 2: Different approach to product demo
        Map<String, String> scene2 = new HashMap<>();
        scene2.put("0_0", "Living room setting with warm lighting");
        scene2.put("0_1", "Person holding product differently");
        scene2.put("0_2", "Product shown from different angle");
        scene2.put("1_0", "Person explaining product in casual manner");
        scene2.put("1_1", "Product being used in different context");
        scene2.put("1_2", "Results shown in different format");
        scene2.put("2_0", "Person smiling while using product");
        scene2.put("2_1", "Product placed on coffee table");
        scene2.put("2_2", "Website URL instead of pricing");
        scenes.add(scene2);
        
        return scenes;
    }
}