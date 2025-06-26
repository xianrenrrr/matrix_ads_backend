package com.example.demo.ai.comparison;

import java.util.Map;

public interface EmbeddingService {
    /**
     * Convert text descriptions to embeddings using AI model
     * @param blockDescriptions Map of "row_col" → description text
     * @return Map of "row_col" → embedding vector
     */
    Map<String, float[]> generateEmbeddings(Map<String, String> blockDescriptions);
    
    /**
     * Generate embedding for a single text
     * @param text The text to embed
     * @return Embedding vector
     */
    float[] generateEmbedding(String text);
}