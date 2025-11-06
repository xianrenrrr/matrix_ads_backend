package com.example.demo.service;

import com.example.demo.ai.subtitle.SubtitleSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for splitting scriptLine text into timed subtitle segments using Qwen AI
 * Used for manual template creation where users provide the full subtitle text
 */
@Service
public class ScriptLineSegmentationService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScriptLineSegmentationService.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${AI_QWEN_ENDPOINT:${qwen.api.base:}}")
    private String qwenApiBase;
    
    @Value("${AI_QWEN_API_KEY:${qwen.api.key:}}")
    private String qwenApiKey;
    
    @Value("${AI_QWEN_MODEL:${qwen.model:qwen-plus}}")
    private String qwenModel;
    
    public ScriptLineSegmentationService() {
        // Configure RestTemplate with timeouts (same as QwenVLPlusLabeler)
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10 seconds connect timeout
        factory.setReadTimeout(60000);     // 60 seconds read timeout
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Split scriptLine into timed subtitle segments using Qwen AI
     * 
     * @param scriptLine Full subtitle text provided by user
     * @param videoDurationSeconds Total video duration in seconds
     * @param sceneStartTimeMs Scene start time in milliseconds (for timing offset)
     * @return List of subtitle segments with timing information (adjusted to scene start time)
     */
    public List<SubtitleSegment> splitScriptLine(String scriptLine, int videoDurationSeconds, long sceneStartTimeMs) {
        List<SubtitleSegment> segments = new ArrayList<>();
        
        if (scriptLine == null || scriptLine.trim().isEmpty()) {
            log.warn("Empty scriptLine provided, returning empty segments");
            return segments;
        }
        
        if (videoDurationSeconds <= 0) {
            log.warn("Invalid video duration: {}, returning empty segments", videoDurationSeconds);
            return segments;
        }
        
        String text = scriptLine.trim();
        log.info("Splitting scriptLine with Qwen AI: length={}, videoDuration={}s, sceneStartTime={}ms", 
                 text.length(), videoDurationSeconds, sceneStartTimeMs);
        
        try {
            // Call Qwen AI to split the text intelligently
            List<Map<String, Object>> aiSegments = callQwenForSegmentation(text, videoDurationSeconds);
            
            if (aiSegments == null || aiSegments.isEmpty()) {
                log.warn("Qwen AI returned no segments, falling back to simple split");
                return fallbackSplit(text, videoDurationSeconds, sceneStartTimeMs);
            }
            
            // Convert AI segments to SubtitleSegment objects and adjust timing
            for (Map<String, Object> aiSeg : aiSegments) {
                long startMs = ((Number) aiSeg.get("startMs")).longValue();
                long endMs = ((Number) aiSeg.get("endMs")).longValue();
                String segText = (String) aiSeg.get("text");
                
                // Add scene start time offset to align with template timeline
                SubtitleSegment segment = new SubtitleSegment(
                    startMs + sceneStartTimeMs,  // Adjust to scene start time
                    endMs + sceneStartTimeMs,    // Adjust to scene start time
                    segText,
                    1.0 // Full confidence for user-provided text
                );
                
                segments.add(segment);
            }
            
            log.info("Created {} subtitle segments from Qwen AI (adjusted to scene start time)", segments.size());
            return segments;
            
        } catch (Exception e) {
            log.error("Failed to call Qwen AI for segmentation: {}", e.getMessage(), e);
            log.warn("Falling back to simple split");
            return fallbackSplit(text, videoDurationSeconds, sceneStartTimeMs);
        }
    }
    
    /**
     * Backward compatible method without scene start time (defaults to 0)
     */
    public List<SubtitleSegment> splitScriptLine(String scriptLine, int videoDurationSeconds) {
        return splitScriptLine(scriptLine, videoDurationSeconds, 0L);
    }
    
    /**
     * Call Qwen AI to intelligently split scriptLine into timed segments
     */
    private List<Map<String, Object>> callQwenForSegmentation(String scriptLine, int videoDurationSeconds) {
        try {
            // Build prompt for Qwen
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是字幕分段助手。请将以下文本智能分段，并为每段分配合理的时间。\n\n");
            prompt.append("【文本内容】\n").append(scriptLine).append("\n\n");
            prompt.append("【视频时长】\n").append(videoDurationSeconds).append(" 秒\n\n");
            prompt.append("任务要求：\n");
            prompt.append("1. 根据语义和标点符号将文本分成多个字幕段\n");
            prompt.append("2. 每段字幕长度适中（中文10-20字，英文30-60字符）\n");
            prompt.append("3. 为每段分配开始和结束时间（毫秒），确保总时长不超过视频时长\n");
            prompt.append("4. 时间分配要考虑阅读速度（中文约3字/秒，英文约15字符/秒）\n");
            prompt.append("5. 段与段之间可以有小间隔（100-200ms）\n\n");
            prompt.append("输出格式（仅JSON，不要任何解释）：\n");
            prompt.append("{\n");
            prompt.append("  \"segments\": [\n");
            prompt.append("    {\"startMs\": 0, \"endMs\": 2500, \"text\": \"第一段字幕文本\"},\n");
            prompt.append("    {\"startMs\": 2700, \"endMs\": 5200, \"text\": \"第二段字幕文本\"}\n");
            prompt.append("  ]\n");
            prompt.append("}\n");
            
            // Build Qwen API request
            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", prompt.toString());
            content.add(textContent);
            
            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);
            
            // Make API call
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            String endpoint = normalizeChatEndpoint(qwenApiBase);
            log.info("Calling Qwen API at: {}", endpoint);
            
            ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Qwen API returned non-success status: {}", response.getStatusCodeValue());
                return null;
            }
            
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                log.error("Qwen API returned empty response");
                return null;
            }
            
            // Extract content from response
            String contentStr = extractContent(responseBody);
            if (contentStr == null || contentStr.isBlank()) {
                log.error("Failed to extract content from Qwen response");
                return null;
            }
            
            // Parse JSON response
            Map<String, Object> result = objectMapper.readValue(contentStr, new TypeReference<Map<String, Object>>() {});
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> segments = (List<Map<String, Object>>) result.get("segments");
            
            if (segments == null || segments.isEmpty()) {
                log.warn("Qwen AI returned no segments in response");
                return null;
            }
            
            log.info("Qwen AI returned {} segments", segments.size());
            return segments;
            
        } catch (Exception e) {
            log.error("Error calling Qwen AI: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Fallback method using simple text splitting (when AI fails)
     */
    private List<SubtitleSegment> fallbackSplit(String text, int videoDurationSeconds, long sceneStartTimeMs) {
        List<SubtitleSegment> segments = new ArrayList<>();
        
        // Detect if text is primarily Chinese or English
        boolean isChinese = containsChinese(text);
        int maxSegmentLength = isChinese ? 20 : 60;
        double charsPerSecond = isChinese ? 3.0 : 15.0;
        
        // Split text into segments based on punctuation and max length
        List<String> textSegments = splitTextIntoSegments(text, maxSegmentLength);
        
        if (textSegments.isEmpty()) {
            return segments;
        }
        
        // Calculate timing for each segment
        long videoDurationMs = videoDurationSeconds * 1000L;
        long currentTimeMs = 0;
        
        for (String segmentText : textSegments) {
            // Calculate duration based on text length and reading speed
            double segmentDurationSeconds = segmentText.length() / charsPerSecond;
            long segmentDurationMs = (long) (segmentDurationSeconds * 1000);
            
            // Ensure segment doesn't exceed video duration
            long endTimeMs = Math.min(currentTimeMs + segmentDurationMs, videoDurationMs);
            
            SubtitleSegment segment = new SubtitleSegment(
                currentTimeMs + sceneStartTimeMs,  // Adjust to scene start time
                endTimeMs + sceneStartTimeMs,      // Adjust to scene start time
                segmentText,
                1.0
            );
            
            segments.add(segment);
            currentTimeMs = endTimeMs;
            
            if (currentTimeMs >= videoDurationMs) {
                break;
            }
        }
        
        // Extend last segment if needed
        if (!segments.isEmpty() && currentTimeMs < videoDurationMs) {
            SubtitleSegment lastSegment = segments.get(segments.size() - 1);
            lastSegment.setEndTimeMs(videoDurationMs + sceneStartTimeMs);
        }
        
        return segments;
    }
    
    /**
     * Normalize Qwen API endpoint to chat completion format
     */
    private String normalizeChatEndpoint(String base) {
        if (base == null || base.isBlank()) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        }
        base = base.trim();
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/")) {
            return base + "chat/completions";
        }
        return base + "/chat/completions";
    }
    
    /**
     * Extract content from Qwen API response
     */
    private String extractContent(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            
            if (message == null) {
                return null;
            }
            
            Object content = message.get("content");
            return content != null ? content.toString().trim() : null;
            
        } catch (Exception e) {
            log.error("Failed to extract content from response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Split text into segments based on punctuation and max length
     */
    private List<String> splitTextIntoSegments(String text, int maxLength) {
        List<String> segments = new ArrayList<>();
        
        // Split by common punctuation marks
        String[] sentences = text.split("[。！？；,.!?;]");
        
        StringBuilder currentSegment = new StringBuilder();
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }
            
            // If adding this sentence would exceed max length, save current segment
            if (currentSegment.length() > 0 && 
                currentSegment.length() + sentence.length() > maxLength) {
                segments.add(currentSegment.toString().trim());
                currentSegment = new StringBuilder();
            }
            
            // Add sentence to current segment
            if (currentSegment.length() > 0) {
                currentSegment.append(" ");
            }
            currentSegment.append(sentence);
            
            // If current segment is already at max length, save it
            if (currentSegment.length() >= maxLength) {
                segments.add(currentSegment.toString().trim());
                currentSegment = new StringBuilder();
            }
        }
        
        // Add remaining text
        if (currentSegment.length() > 0) {
            segments.add(currentSegment.toString().trim());
        }
        
        // If no segments were created (no punctuation), split by max length
        if (segments.isEmpty() && !text.isEmpty()) {
            for (int i = 0; i < text.length(); i += maxLength) {
                int end = Math.min(i + maxLength, text.length());
                segments.add(text.substring(i, end).trim());
            }
        }
        
        return segments;
    }
    
    /**
     * Check if text contains Chinese characters
     */
    private boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        int chineseCount = 0;
        int totalCount = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                totalCount++;
                if (isChinese(c)) {
                    chineseCount++;
                }
            }
        }
        
        // Consider text as Chinese if more than 30% of characters are Chinese
        return totalCount > 0 && (chineseCount * 1.0 / totalCount) > 0.3;
    }
    
    /**
     * Check if a character is Chinese
     */
    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }
}
