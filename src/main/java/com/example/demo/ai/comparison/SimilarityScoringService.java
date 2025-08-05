package com.example.demo.ai.comparison;

import java.util.List;
import java.util.Map;

public interface SimilarityScoringService {
    /**
     * Calculate cosine similarity between two embedding vectors
     * @param vector1 First embedding vector
     * @param vector2 Second embedding vector
     * @return Cosine similarity score (0.0 to 1.0)
     */
    double calculateCosineSimilarity(float[] vector1, float[] vector2);
    
    /**
     * Compare all embeddings and generate comprehensive comparison result
     * @param templateEmbeddings Template video block embeddings grouped by scene
     * @param userEmbeddings User video block embeddings grouped by scene
     * @return Complete comparison result with overall and per-scene scores
     */
    ComparisonResult compareEmbeddings(
        List<Map<String, float[]>> templateEmbeddings, 
        List<Map<String, float[]>> userEmbeddings
    );
    
    /**
     * Compare embeddings for a single scene
     * @param templateBlocks Template scene block embeddings
     * @param userBlocks User scene block embeddings
     * @return Scene comparison with block-level scores
     */
    SceneComparison compareScene(
        Map<String, float[]> templateBlocks, 
        Map<String, float[]> userBlocks
    );
}