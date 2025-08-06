package com.example.demo.controller.contentmanager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-test")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class AITestController {
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;
    
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> testAIConfiguration() {
        Map<String, Object> response = new HashMap<>();
        
        boolean hasApiKey = openaiApiKey != null && !openaiApiKey.trim().isEmpty();
        
        response.put("success", true);
        response.put("openaiConfigured", hasApiKey);
        
        if (hasApiKey) {
            response.put("message", "OpenAI API key is configured");
            response.put("keyPreview", "sk-" + "*".repeat(20) + openaiApiKey.substring(Math.max(0, openaiApiKey.length() - 4)));
        } else {
            response.put("message", "OpenAI API key is not configured");
            response.put("error", "Please set OPENAI_API_KEY environment variable");
        }
        
        response.put("environmentVariables", Map.of(
            "OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") != null ? "Set" : "Not set",
            "openai.api.key", System.getProperty("openai.api.key") != null ? "Set" : "Not set"
        ));
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/generate-template")
    public ResponseEntity<Map<String, Object>> testTemplateGeneration(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        String videoId = (String) request.get("videoId");
        String title = (String) request.get("title");
        
        boolean hasApiKey = openaiApiKey != null && !openaiApiKey.trim().isEmpty();
        
        if (!hasApiKey) {
            response.put("success", false);
            response.put("error", "OpenAI API key not configured");
            response.put("message", "Please set OPENAI_API_KEY environment variable on Render");
            return ResponseEntity.badRequest().body(response);
        }
        
        response.put("success", true);
        response.put("message", "AI template generation would start here");
        response.put("videoId", videoId);
        response.put("title", title);
        response.put("estimatedScenes", 8);
        response.put("status", "Template generation simulation successful");
        
        return ResponseEntity.ok(response);
    }
}