package com.example.demo.ai.providers.llm;

import com.example.demo.model.Video;
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
import java.util.stream.Collectors;

@Service
public class VideoSummaryServiceImpl implements VideoSummaryService {
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;
    
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions) {
        return generateSummary(video, sceneLabels, allBlockDescriptions, "en");
    }
    
    public String generateSummary(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions, String language) {
        System.out.printf("Generating video summary for: %s%n", video.getTitle());
        
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            System.err.println("OpenAI API key not configured. Using fallback summary.");
            return String.format("AI-generated template for '%s' with %d detected scenes", 
                                video.getTitle(), sceneLabels.size());
        }
        
        try {
            String prompt = buildSummaryPrompt(video, sceneLabels, allBlockDescriptions, language);
            return callGPT4(prompt);
            
        } catch (Exception e) {
            System.err.printf("Error generating video summary: %s%n", e.getMessage());
            return String.format("Template generated from '%s' containing detected scenes and visual elements", 
                                video.getTitle());
        }
    }
    
    private String buildSummaryPrompt(Video video, List<String> sceneLabels, Map<String, String> allBlockDescriptions, String language) {
        StringBuilder prompt = new StringBuilder();
        
        // Add language instruction if Chinese
        if ("zh".equals(language)) {
            prompt.append("请用中文回答。\n\n");
        }
        
        prompt.append("Generate a 1-2 sentence summary for a video marketing template based on the following information:\n\n");
        prompt.append("Video Title: ").append(video.getTitle()).append("\n");
        
        if (video.getDescription() != null && !video.getDescription().trim().isEmpty()) {
            prompt.append("Video Description: ").append(video.getDescription()).append("\n");
        }
        
        prompt.append("Detected Scene Labels: ").append(String.join(", ", sceneLabels)).append("\n");
        
        if (!allBlockDescriptions.isEmpty()) {
            List<String> uniqueDescriptions = allBlockDescriptions.values().stream()
                .distinct()
                .limit(10) // Limit to avoid too long prompt
                .collect(Collectors.toList());
            prompt.append("Visual Elements: ").append(String.join("; ", uniqueDescriptions)).append("\n");
        }
        
        prompt.append("\nPlease create a concise, marketing-focused summary that describes what this video template would help users create.");
        
        String finalPrompt = prompt.toString();
        
        // LOG: What we're sending to AI
        System.out.println("=== AI VIDEO SUMMARY REQUEST ===");
        System.out.println("Language: " + language);
        System.out.println("Prompt being sent to AI: " + finalPrompt);
        System.out.println("================================");
        
        return finalPrompt;
    }
    
    private String callGPT4(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + openaiApiKey);
        headers.set("Content-Type", "application/json");
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("max_tokens", 150);
        requestBody.put("temperature", 0.7);
        
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        
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
                    Map<String, Object> message1 = (Map<String, Object>) firstChoice.get("message");
                    String aiResponse = (String) message1.get("content");
                    
                    // LOG: What AI responded
                    System.out.println("=== AI VIDEO SUMMARY RESPONSE ===");
                    System.out.println("AI Response: " + aiResponse);
                    System.out.println("==================================");
                    
                    return aiResponse;
                }
            }
            
            return "AI-powered video template with detected scenes and visual elements";
            
        } catch (Exception e) {
            System.err.printf("Error calling GPT-4 API: %s%n", e.getMessage());
            throw new RuntimeException("Failed to generate summary", e);
        }
    }
}