package com.example.demo.ai.shared;

import com.example.demo.config.DeepSeekConfig;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    @Autowired
    private DeepSeekConfig deepSeekConfig;
    
    // Using OpenAI GPT-4o-mini (cheaper alternative since DeepSeek API doesn't support vision)
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, String> describeBlocks(Map<String, String> blockImageUrls) {
        return describeBlocks(blockImageUrls, "en");
    }
    
    public Map<String, String> describeBlocks(Map<String, String> blockImageUrls, String language) {
        System.out.printf("Describing %d image blocks using GPT-4o-mini (vision)%n", blockImageUrls.size());
        
        Map<String, String> blockDescriptions = new HashMap<>();
        
        String effectiveApiKey = deepSeekConfig.getApi().getEffectiveKey();
        if (effectiveApiKey == null || effectiveApiKey.trim().isEmpty()) {
            System.err.println("OpenAI API key not configured. Using fallback descriptions.");
            System.err.println("Please set OPENAI_API_KEY environment variable or add openai.api.key.local in application-local.properties");
            // Provide fallback descriptions
            for (String blockKey : blockImageUrls.keySet()) {
                blockDescriptions.put(blockKey, "Image content description not available (OpenAI API key not configured)");
            }
            return blockDescriptions;
        }
        
        System.out.printf("Using OpenAI API key from: %s%n", 
            (deepSeekConfig.getApi().getKey() != null && !deepSeekConfig.getApi().getKey().isEmpty()) 
                ? "environment variable" : "local configuration file");
        
        for (Map.Entry<String, String> entry : blockImageUrls.entrySet()) {
            String blockKey = entry.getKey();
            String imageUrl = entry.getValue();
            
            try {
                String description = describeImage(imageUrl, language);
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
    
    private String describeImage(String imageUrl, String language) {
        String effectiveApiKey = deepSeekConfig.getApi().getEffectiveKey();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + effectiveApiKey);
        headers.set("Content-Type", "application/json");
        
        // Build request body for GPT-4o-mini with vision
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("max_tokens", 100);
        requestBody.put("temperature", 0.3);
        
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        
        // Content with text and image for DeepSeek format
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        
        // Add language instruction if Chinese
        String textPrompt = "Describe the visual content of this image in one concise sentence. Focus on the main objects, people, or elements visible.";
        if ("zh".equals(language)) {
            textPrompt = "请用中文回答。" + textPrompt;
        }
        textContent.put("text", textPrompt);
        
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
                OPENAI_API_URL, 
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
            System.err.printf("Error calling GPT-4o-mini API for image %s: %s%n", imageUrl, e.getMessage());
            throw new RuntimeException("Failed to describe image", e);
        }
    }
}