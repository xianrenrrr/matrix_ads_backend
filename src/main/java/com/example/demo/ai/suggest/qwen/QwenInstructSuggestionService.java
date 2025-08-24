package com.example.demo.ai.suggest.qwen;

import com.example.demo.ai.suggest.SuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Service
public class QwenInstructSuggestionService implements SuggestionService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${qwen.api.base:}")
    private String qwenApiBase;
    
    @Value("${qwen.api.key:}")
    private String qwenApiKey;
    
    @Value("${qwen.suggest.model:qwen-2.5-instruct}")
    private String qwenModel;
    
    @Value("${qwen.timeout.ms:15000}")
    private int qwenTimeout;
    
    public QwenInstructSuggestionService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public SuggestionsResult suggestCn(Map<String, Object> comparisonFacts) {
        String suggestions = callQwenForSuggestions(comparisonFacts);
        
        // Parse the response
        SuggestionsResult result = parseSuggestions(suggestions);
        
        // Validate and retry if needed
        if (!isValidResult(result)) {
            suggestions = callQwenForSuggestionsStricter(comparisonFacts);
            result = parseSuggestions(suggestions);
            
            if (!isValidResult(result)) {
                // Fallback to default suggestions
                return new SuggestionsResult(
                    Arrays.asList("调整拍摄角度", "保持稳定", "注意光线"),
                    Arrays.asList("重新录制", "查看示例")
                );
            }
        }
        
        return result;
    }
    
    private String callQwenForSuggestions(Map<String, Object> facts) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            
            // System message
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是视频拍摄指导专家。根据对比结果提供简洁中文建议。必须返回JSON格式。");
            messages.add(systemMessage);
            
            // User message with facts
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            String factsJson = objectMapper.writeValueAsString(facts);
            userMessage.put("content", "对比结果：" + factsJson + "\n返回JSON格式：{\"suggestions\":[2-4条建议,每条≤40字],\"actions\":[1-2个操作,每条≤20字]}");
            messages.add(userMessage);
            
            request.put("messages", messages);
            request.put("temperature", 0.3);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                qwenApiBase + "/chat/completions",
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return extractContent(response.getBody());
            }
        } catch (Exception e) {
            System.err.println("Qwen suggestions API call failed: " + e.getMessage());
        }
        
        return "{}";
    }
    
    private String callQwenForSuggestionsStricter(Map<String, Object> facts) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            
            // System message
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "严格要求：只返回JSON，无其他内容。格式：{\"suggestions\":[...],\"actions\":[...]}");
            messages.add(systemMessage);
            
            // User message with facts
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            String factsJson = objectMapper.writeValueAsString(facts);
            userMessage.put("content", "数据：" + factsJson + "\n必须返回：{\"suggestions\":[\"建议1\",\"建议2\"],\"actions\":[\"操作1\"]}");
            messages.add(userMessage);
            
            request.put("messages", messages);
            request.put("temperature", 0.1);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                qwenApiBase + "/chat/completions",
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return extractContent(response.getBody());
            }
        } catch (Exception e) {
            System.err.println("Qwen stricter suggestions API call failed: " + e.getMessage());
        }
        
        return "{}";
    }
    
    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0)
                    .path("message")
                    .path("content")
                    .asText("{}")
                    .trim();
            }
        } catch (Exception e) {
            System.err.println("Failed to extract content: " + e.getMessage());
        }
        
        return "{}";
    }
    
    private SuggestionsResult parseSuggestions(String jsonStr) {
        try {
            // Try to extract JSON from the response
            int startIdx = jsonStr.indexOf('{');
            int endIdx = jsonStr.lastIndexOf('}');
            
            if (startIdx >= 0 && endIdx > startIdx) {
                jsonStr = jsonStr.substring(startIdx, endIdx + 1);
            }
            
            JsonNode root = objectMapper.readTree(jsonStr);
            
            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = root.path("suggestions");
            if (suggestionsNode.isArray()) {
                for (JsonNode suggestion : suggestionsNode) {
                    String text = suggestion.asText().trim();
                    if (!text.isEmpty() && text.length() <= 40) {
                        suggestions.add(text);
                    }
                }
            }
            
            List<String> actions = new ArrayList<>();
            JsonNode actionsNode = root.path("actions");
            if (actionsNode.isArray()) {
                for (JsonNode action : actionsNode) {
                    String text = action.asText().trim();
                    if (!text.isEmpty() && text.length() <= 20) {
                        actions.add(text);
                    }
                }
            }
            
            return new SuggestionsResult(suggestions, actions);
            
        } catch (Exception e) {
            System.err.println("Failed to parse suggestions: " + e.getMessage());
            return new SuggestionsResult(Collections.emptyList(), Collections.emptyList());
        }
    }
    
    private boolean isValidResult(SuggestionsResult result) {
        return result != null &&
               result.suggestionsZh() != null &&
               !result.suggestionsZh().isEmpty() &&
               result.suggestionsZh().size() >= 2 &&
               result.suggestionsZh().size() <= 4 &&
               result.nextActionsZh() != null &&
               result.nextActionsZh().size() >= 1 &&
               result.nextActionsZh().size() <= 2;
    }
}