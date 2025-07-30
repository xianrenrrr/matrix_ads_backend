package com.example.demo.ai.shared;

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
public class BlockDescriptionServiceImpl implements BlockDescriptionService {
    
    @Value("${deepseek.api.key:}")
    private String deepseekApiKey;
    
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, String> describeBlocks(Map<String, String> blockImageUrls) {
        System.out.printf("Describing %d image blocks using DeepSeek-VL%n", blockImageUrls.size());
        
        Map<String, String> blockDescriptions = new HashMap<>();
        
        if (deepseekApiKey == null || deepseekApiKey.trim().isEmpty()) {
            System.err.println("DeepSeek API key not configured. Using fallback descriptions.");
            // Provide fallback descriptions
            for (String blockKey : blockImageUrls.keySet()) {
                blockDescriptions.put(blockKey, "Image content description not available (DeepSeek API key not configured)");
            }
            return blockDescriptions;
        }
        
        for (Map.Entry<String, String> entry : blockImageUrls.entrySet()) {
            String blockKey = entry.getKey();
            String imageUrl = entry.getValue();
            
            try {
                String description = describeImage(imageUrl);
                blockDescriptions.put(blockKey, description);
                System.out.printf("Block %s described: %s%n", blockKey, description);
                
                // Add small delay to avoid rate limiting
                Thread.sleep(100);
                
            } catch (Exception e) {
                System.err.printf("Error describing block %s: %s%n", blockKey, e.getMessage());
                blockDescriptions.put(blockKey, "Description unavailable due to processing error");
            }
        }
        
        return blockDescriptions;
    }
    
    private String describeImage(String imageUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + deepseekApiKey);
        headers.set("Content-Type", "application/json");
        
        // Build request body for DeepSeek-VL with vision
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-vl");
        requestBody.put("max_tokens", 100);
        requestBody.put("temperature", 0.3);
        
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        
        // Content with text and image for DeepSeek format
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "Describe the visual content of this image in one concise sentence. Focus on the main objects, people, or elements visible.");
        
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, String> imageUrlMap = new HashMap<>();
        imageUrlMap.put("url", imageUrl);
        imageContent.put("image_url", imageUrlMap);
        
        message.put("content", List.of(textContent, imageContent));
        requestBody.put("messages", List.of(message));
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                DEEPSEEK_API_URL, 
                HttpMethod.POST, 
                request, 
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> messageResponse = (Map<String, Object>) firstChoice.get("message");
                    return (String) messageResponse.get("content");
                }
            }
            
            return "No description available";
            
        } catch (Exception e) {
            System.err.printf("Error calling DeepSeek-VL API for image %s: %s%n", imageUrl, e.getMessage());
            throw new RuntimeException("Failed to describe image", e);
        }
    }
}