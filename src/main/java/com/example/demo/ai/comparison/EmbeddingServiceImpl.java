package com.example.demo.ai.comparison;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;
    
    private static final String OPENAI_EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";
    private static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, float[]> generateEmbeddings(Map<String, String> blockDescriptions) {
        System.out.printf("Generating embeddings for %d block descriptions%n", blockDescriptions.size());
        
        Map<String, float[]> embeddings = new HashMap<>();
        
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            System.err.println("OpenAI API key not configured. Using fallback embeddings.");
            // Create fallback random embeddings for testing
            for (String blockKey : blockDescriptions.keySet()) {
                embeddings.put(blockKey, generateFallbackEmbedding(blockDescriptions.get(blockKey)));
            }
            return embeddings;
        }
        
        // Process embeddings in batches to avoid API limits
        for (Map.Entry<String, String> entry : blockDescriptions.entrySet()) {
            String blockKey = entry.getKey();
            String description = entry.getValue();
            
            try {
                float[] embedding = generateEmbedding(description);
                embeddings.put(blockKey, embedding);
                System.out.printf("Generated embedding for block %s: %s%n", blockKey, description);
                
                // Small delay to avoid rate limiting
                Thread.sleep(50);
                
            } catch (Exception e) {
                System.err.printf("Error generating embedding for block %s: %s%n", blockKey, e.getMessage());
                // Use fallback embedding on error
                embeddings.put(blockKey, generateFallbackEmbedding(description));
            }
        }
        
        return embeddings;
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            return generateFallbackEmbedding(text);
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + openaiApiKey);
        headers.set("Content-Type", "application/json");
        
        // Build request body for OpenAI Embeddings API
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", EMBEDDING_MODEL);
        requestBody.put("input", text);
        requestBody.put("encoding_format", "float");
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                OPENAI_EMBEDDINGS_URL, 
                HttpMethod.POST, 
                request, 
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("data")) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseBody.get("data");
                if (!dataList.isEmpty()) {
                    Map<String, Object> firstItem = dataList.get(0);
                    List<Double> embeddingList = (List<Double>) firstItem.get("embedding");
                    
                    // Convert List<Double> to float[]
                    float[] embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = embeddingList.get(i).floatValue();
                    }
                    
                    return embedding;
                }
            }
            
            throw new RuntimeException("Invalid response format from OpenAI API");
            
        } catch (Exception e) {
            System.err.printf("Error calling OpenAI Embeddings API for text: %s - %s%n", text, e.getMessage());
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }
    
    /**
     * Generate a simple fallback embedding based on text hash
     * This is used when OpenAI API is not available
     */
    private float[] generateFallbackEmbedding(String text) {
        // Create a simple hash-based embedding for testing
        int hashCode = text.hashCode();
        float[] embedding = new float[1536]; // Same size as text-embedding-3-small
        
        // Fill with pseudo-random values based on hash
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.sin(hashCode + i) * 0.1);
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
}