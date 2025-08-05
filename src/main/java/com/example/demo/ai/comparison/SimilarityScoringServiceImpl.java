package com.example.demo.ai.comparison;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SimilarityScoringServiceImpl implements SimilarityScoringService {

    @Override
    public double calculateCosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException(
                String.format("Vector dimensions must match: %d vs %d", vector1.length, vector2.length)
            );
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        // Avoid division by zero
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        double cosineSimilarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
        
        // Convert from [-1, 1] range to [0, 1] range for easier interpretation
        return (cosineSimilarity + 1.0) / 2.0;
    }

    @Override
    public ComparisonResult compareEmbeddings(
            List<Map<String, float[]>> templateEmbeddings, 
            List<Map<String, float[]>> userEmbeddings) {
        
        System.out.printf("Comparing embeddings: %d template scenes vs %d user scenes%n", 
                         templateEmbeddings.size(), userEmbeddings.size());
        
        List<SceneComparison> sceneComparisons = new ArrayList<>();
        double totalScore = 0.0;
        int validScenes = 0;
        
        // Compare scenes up to the minimum count
        int maxScenes = Math.min(templateEmbeddings.size(), userEmbeddings.size());
        
        for (int i = 0; i < maxScenes; i++) {
            Map<String, float[]> templateScene = templateEmbeddings.get(i);
            Map<String, float[]> userScene = userEmbeddings.get(i);
            
            SceneComparison sceneComparison = compareScene(templateScene, userScene);
            sceneComparison.setSceneIndex(i);
            
            sceneComparisons.add(sceneComparison);
            totalScore += sceneComparison.getSimilarity();
            validScenes++;
            
            System.out.printf("Scene %d similarity: %.3f%n", i, sceneComparison.getSimilarity());
        }
        
        // Handle mismatched scene counts
        if (templateEmbeddings.size() != userEmbeddings.size()) {
            System.out.printf("Warning: Scene count mismatch - template: %d, user: %d%n", 
                             templateEmbeddings.size(), userEmbeddings.size());
        }
        
        double overallScore = validScenes > 0 ? totalScore / validScenes : 0.0;
        
        ComparisonResult result = new ComparisonResult(overallScore, sceneComparisons);
        System.out.printf("Overall similarity score: %.3f%n", overallScore);
        
        return result;
    }

    @Override
    public SceneComparison compareScene(
            Map<String, float[]> templateBlocks, 
            Map<String, float[]> userBlocks) {
        
        Map<String, Double> blockScores = new HashMap<>();
        double totalSimilarity = 0.0;
        int validBlocks = 0;
        
        // Standard 3x3 grid block positions
        String[] blockPositions = {"0_0", "0_1", "0_2", "1_0", "1_1", "1_2", "2_0", "2_1", "2_2"};
        
        for (String blockPos : blockPositions) {
            float[] templateEmbedding = templateBlocks.get(blockPos);
            float[] userEmbedding = userBlocks.get(blockPos);
            
            if (templateEmbedding != null && userEmbedding != null) {
                double similarity = calculateCosineSimilarity(templateEmbedding, userEmbedding);
                blockScores.put(blockPos, similarity);
                totalSimilarity += similarity;
                validBlocks++;
                
                System.out.printf("Block %s similarity: %.3f%n", blockPos, similarity);
            } else {
                // Handle missing blocks
                blockScores.put(blockPos, 0.0);
                System.out.printf("Block %s missing in %s%n", blockPos, 
                                templateEmbedding == null ? "template" : "user video");
            }
        }
        
        double sceneSimilarity = validBlocks > 0 ? totalSimilarity / validBlocks : 0.0;
        
        return new SceneComparison(0, sceneSimilarity, blockScores);
    }
}