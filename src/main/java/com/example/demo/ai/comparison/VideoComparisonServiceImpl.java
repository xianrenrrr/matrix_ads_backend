package com.example.demo.ai.comparison;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VideoComparisonServiceImpl implements VideoComparisonService {

    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private SimilarityScoringService similarityScoringService;

    @Override
    public ComparisonResult compareVideoToTemplate(
            List<Map<String, String>> templateScenes, 
            List<Map<String, String>> userScenes) {
        
        System.out.printf("Starting video comparison: %d template scenes vs %d user scenes%n", 
                         templateScenes.size(), userScenes.size());
        
        try {
            // Step 1: Generate embeddings for template scenes
            System.out.println("Step 1: Generating embeddings for template scenes...");
            List<Map<String, float[]>> templateEmbeddings = new ArrayList<>();
            
            for (int i = 0; i < templateScenes.size(); i++) {
                Map<String, String> sceneDescriptions = templateScenes.get(i);
                Map<String, float[]> sceneEmbeddings = embeddingService.generateEmbeddings(sceneDescriptions);
                templateEmbeddings.add(sceneEmbeddings);
                System.out.printf("Generated embeddings for template scene %d (%d blocks)%n", 
                                 i, sceneEmbeddings.size());
            }
            
            // Step 2: Generate embeddings for user scenes
            System.out.println("Step 2: Generating embeddings for user scenes...");
            List<Map<String, float[]>> userEmbeddings = new ArrayList<>();
            
            for (int i = 0; i < userScenes.size(); i++) {
                Map<String, String> sceneDescriptions = userScenes.get(i);
                Map<String, float[]> sceneEmbeddings = embeddingService.generateEmbeddings(sceneDescriptions);
                userEmbeddings.add(sceneEmbeddings);
                System.out.printf("Generated embeddings for user scene %d (%d blocks)%n", 
                                 i, sceneEmbeddings.size());
            }
            
            // Step 3: Compare embeddings and calculate similarity scores
            System.out.println("Step 3: Calculating similarity scores...");
            ComparisonResult result = similarityScoringService.compareEmbeddings(
                templateEmbeddings, userEmbeddings
            );
            
            System.out.printf("Video comparison completed. Overall similarity: %.3f%n", 
                             result.getOverallScore());
            return result;
            
        } catch (Exception e) {
            System.err.printf("Error during video comparison: %s%n", e.getMessage());
            e.printStackTrace();
            
            // Return a fallback result
            return createFallbackResult(templateScenes.size(), userScenes.size());
        }
    }

    @Override
    public ComparisonResult compareTemplates(ManualTemplate template1, ManualTemplate template2) {
        System.out.printf("Comparing templates: '%s' vs '%s'%n", 
                         template1.getTemplateTitle(), template2.getTemplateTitle());
        
        // Extract block descriptions from template scenes
        List<Map<String, String>> template1Scenes = extractScenesFromTemplate(template1);
        List<Map<String, String>> template2Scenes = extractScenesFromTemplate(template2);
        
        return compareVideoToTemplate(template1Scenes, template2Scenes);
    }

    @Override
    public String generateComparisonReport(ComparisonResult result) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== VIDEO COMPARISON REPORT ===\n\n");
        report.append(String.format("Overall Similarity Score: %.1f%%\n", result.getOverallScore() * 100));
        report.append(String.format("Total Scenes Compared: %d\n\n", result.getSceneComparisons().size()));
        
        // Classification based on overall score
        String classification;
        if (result.getOverallScore() >= 0.8) {
            classification = "EXCELLENT MATCH";
        } else if (result.getOverallScore() >= 0.6) {
            classification = "GOOD MATCH";
        } else if (result.getOverallScore() >= 0.4) {
            classification = "MODERATE MATCH";
        } else if (result.getOverallScore() >= 0.2) {
            classification = "POOR MATCH";
        } else {
            classification = "VERY POOR MATCH";
        }
        
        report.append(String.format("Match Quality: %s\n\n", classification));
        
        // Scene-by-scene breakdown
        report.append("=== SCENE-BY-SCENE ANALYSIS ===\n\n");
        
        for (SceneComparison scene : result.getSceneComparisons()) {
            report.append(String.format("Scene %d: %.1f%% similarity\n", 
                                       scene.getSceneIndex() + 1, scene.getSimilarity() * 100));
            
            // Find best and worst blocks
            if (scene.getBlockScores() != null && !scene.getBlockScores().isEmpty()) {
                String bestBlock = "";
                String worstBlock = "";
                double bestScore = 0.0;
                double worstScore = 1.0;
                
                for (Map.Entry<String, Double> block : scene.getBlockScores().entrySet()) {
                    if (block.getValue() > bestScore) {
                        bestScore = block.getValue();
                        bestBlock = block.getKey();
                    }
                    if (block.getValue() < worstScore) {
                        worstScore = block.getValue();
                        worstBlock = block.getKey();
                    }
                }
                
                report.append(String.format("  Best match: Block %s (%.1f%%)\n", 
                                           bestBlock, bestScore * 100));
                report.append(String.format("  Worst match: Block %s (%.1f%%)\n", 
                                           worstBlock, worstScore * 100));
            }
            report.append("\n");
        }
        
        // Summary and recommendations
        report.append("=== RECOMMENDATIONS ===\n\n");
        
        if (result.getOverallScore() >= 0.7) {
            report.append("✓ This video closely follows the template guidelines.\n");
            report.append("✓ Content alignment is strong across most scenes.\n");
        } else if (result.getOverallScore() >= 0.5) {
            report.append("⚠ This video partially follows the template.\n");
            report.append("⚠ Consider reviewing scenes with lower similarity scores.\n");
        } else {
            report.append("✗ This video significantly deviates from the template.\n");
            report.append("✗ Major revisions recommended to improve alignment.\n");
        }
        
        return report.toString();
    }
    
    /**
     * Extract block descriptions from a ManualTemplate
     */
    private List<Map<String, String>> extractScenesFromTemplate(ManualTemplate template) {
        List<Map<String, String>> scenes = new ArrayList<>();
        
        if (template.getScenes() != null) {
            for (Scene scene : template.getScenes()) {
                Map<String, String> sceneDescriptions = new HashMap<>();
                
                // Use block descriptions if available
                if (scene.getBlockDescriptions() != null) {
                    sceneDescriptions.putAll(scene.getBlockDescriptions());
                } else {
                    // Fallback to script line for all blocks if block descriptions not available
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
     * Create a fallback result when comparison fails
     */
    private ComparisonResult createFallbackResult(int templateSceneCount, int userSceneCount) {
        List<SceneComparison> fallbackScenes = new ArrayList<>();
        
        int maxScenes = Math.min(templateSceneCount, userSceneCount);
        for (int i = 0; i < maxScenes; i++) {
            Map<String, Double> fallbackBlocks = new HashMap<>();
            // Fill with low similarity scores
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    fallbackBlocks.put(row + "_" + col, 0.1);
                }
            }
            fallbackScenes.add(new SceneComparison(i, 0.1, fallbackBlocks));
        }
        
        return new ComparisonResult(0.1, fallbackScenes);
    }
}