package com.example.demo.ai.providers.embedding;

import com.example.demo.ai.core.AIModelProvider;
import com.example.demo.ai.core.AIResponse;
import java.util.List;

/**
 * Interface for embedding model providers
 * Handles vector representations and similarity calculations
 */
public interface EmbeddingProvider extends AIModelProvider {
    
    /**
     * Generate embedding vector for text
     * @param text Input text to embed
     * @return AIResponse containing embedding vector
     */
    AIResponse<float[]> generateTextEmbedding(String text);
    
    /**
     * Generate embedding vector for image
     * @param imageUrl URL of image to embed
     * @return AIResponse containing embedding vector
     */
    AIResponse<float[]> generateImageEmbedding(String imageUrl);
    
    /**
     * Calculate similarity between two embedding vectors
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return AIResponse containing similarity score (0.0 to 1.0)
     */
    AIResponse<Double> calculateSimilarity(float[] embedding1, float[] embedding2);
    
    /**
     * Batch generate embeddings for multiple texts
     * @param texts List of texts to embed
     * @return AIResponse containing list of embedding vectors
     */
    AIResponse<List<float[]>> generateTextEmbeddingsBatch(List<String> texts);
    
    /**
     * Get the dimension of embeddings produced by this provider
     * @return Embedding vector dimension
     */
    int getEmbeddingDimension();
}