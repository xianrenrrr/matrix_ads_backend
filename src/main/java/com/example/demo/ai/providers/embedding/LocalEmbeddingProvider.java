package com.example.demo.ai.providers.embedding;

import com.example.demo.ai.core.AIModelType;
import com.example.demo.ai.core.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Local embedding provider for basic similarity calculations
 * Uses simple hashing and similarity metrics for basic text/image comparisons
 */
@Service
public class LocalEmbeddingProvider implements EmbeddingProvider {
    
    private static final Logger log = LoggerFactory.getLogger(LocalEmbeddingProvider.class);
    
    @Value("${ai.providers.embedding.dimension:384}")
    private int embeddingDimension;
    
    private final Random random = new Random(42); // Fixed seed for consistency
    
    @Override
    public AIResponse<float[]> generateTextEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return AIResponse.error("Text input is empty");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // TODO: Replace with actual embedding model (e.g., sentence-transformers)
            // For now, generate deterministic "embedding" based on text hash
            float[] embedding = generateMockEmbedding(text);
            
            AIResponse<float[]> response = AIResponse.success(embedding, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("text_length", text.length());
            metadata.put("dimension", embeddingDimension);
            response.setMetadata(metadata);
            
            return response;
            
        } catch (Exception e) {
            log.error("Text embedding generation failed: {}", e.getMessage(), e);
            return AIResponse.error("Embedding error: " + e.getMessage());
        }
    }
    
    @Override
    public AIResponse<float[]> generateImageEmbedding(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return AIResponse.error("Image URL is empty");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // TODO: Replace with actual image embedding model (e.g., CLIP)
            // For now, generate embedding based on URL hash
            float[] embedding = generateMockEmbedding(imageUrl);
            
            AIResponse<float[]> response = AIResponse.success(embedding, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("image_url", imageUrl);
            metadata.put("dimension", embeddingDimension);
            response.setMetadata(metadata);
            
            return response;
            
        } catch (Exception e) {
            log.error("Image embedding generation failed: {}", e.getMessage(), e);
            return AIResponse.error("Embedding error: " + e.getMessage());
        }
    }
    
    @Override
    public AIResponse<Double> calculateSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null) {
            return AIResponse.error("Embeddings cannot be null");
        }
        
        if (embedding1.length != embedding2.length) {
            return AIResponse.error("Embedding dimensions must match");
        }
        
        try {
            double similarity = cosineSimilarity(embedding1, embedding2);
            return AIResponse.success(similarity, getProviderName(), getModelType());
            
        } catch (Exception e) {
            log.error("Similarity calculation failed: {}", e.getMessage(), e);
            return AIResponse.error("Similarity error: " + e.getMessage());
        }
    }
    
    @Override
    public AIResponse<List<float[]>> generateTextEmbeddingsBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return AIResponse.error("Text list is empty");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            List<float[]> embeddings = new ArrayList<>();
            for (String text : texts) {
                AIResponse<float[]> result = generateTextEmbedding(text);
                if (result.isSuccess()) {
                    embeddings.add(result.getData());
                } else {
                    log.warn("Failed to generate embedding for text: {}", text);
                    embeddings.add(new float[embeddingDimension]); // Zero vector as fallback
                }
            }
            
            AIResponse<List<float[]>> response = AIResponse.success(embeddings, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("batch_size", texts.size());
            metadata.put("dimension", embeddingDimension);
            response.setMetadata(metadata);
            
            return response;
            
        } catch (Exception e) {
            log.error("Batch embedding generation failed: {}", e.getMessage(), e);
            return AIResponse.error("Batch embedding error: " + e.getMessage());
        }
    }
    
    @Override
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }
    
    // =========================
    // AIModelProvider Interface
    // =========================
    
    @Override
    public AIModelType getModelType() {
        return AIModelType.EMBEDDING;
    }
    
    @Override
    public String getProviderName() {
        return "LocalEmbedding";
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Always available for local computation
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("dimension", embeddingDimension);
        config.put("type", "local_hash");
        return config;
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        if (config.containsKey("dimension")) {
            this.embeddingDimension = (Integer) config.get("dimension");
        }
        log.info("Local embedding provider initialized with dimension: {}", embeddingDimension);
    }
    
    @Override
    public int getPriority() {
        return 10; // Low priority - fallback option
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        switch (operation) {
            case "generateTextEmbedding":
            case "generateImageEmbedding":
            case "calculateSimilarity":
            case "generateTextEmbeddingsBatch":
                return true;
            default:
                return false;
        }
    }
    
    // =========================
    // Private Methods
    // =========================
    
    /**
     * Generate mock embedding based on string hash - replace with actual model
     */
    private float[] generateMockEmbedding(String input) {
        float[] embedding = new float[embeddingDimension];
        
        // Use hash code to seed random generation for consistency
        Random seededRandom = new Random(input.hashCode());
        
        for (int i = 0; i < embeddingDimension; i++) {
            embedding[i] = (seededRandom.nextFloat() - 0.5f) * 2; // Range [-1, 1]
        }
        
        // Normalize the vector
        float norm = 0;
        for (float value : embedding) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }
        
        return embedding;
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        double magnitude = Math.sqrt(normA) * Math.sqrt(normB);
        return magnitude == 0 ? 0 : dotProduct / magnitude;
    }
}