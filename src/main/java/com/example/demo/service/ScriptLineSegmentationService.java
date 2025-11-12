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
            // Check if Qwen is configured
            if (qwenApiKey == null || qwenApiKey.isBlank() || qwenApiBase == null || qwenApiBase.isBlank()) {
                log.warn("Qwen API not configured (apiKey or endpoint missing), skipping AI segmentation");
                return null;
            }
            
            // Build prompt for Qwen
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是KTV字幕分段助手。请将以下文本分成多个短小的字幕段，用于KTV风格的逐字显示。\n\n");
            prompt.append("【文本内容】\n").append(scriptLine).append("\n\n");
            prompt.append("【视频时长】\n").append(videoDurationSeconds).append(" 秒\n\n");
            prompt.append("任务要求：\n");
            prompt.append("1. **必须分成多个短段**：每段中文5-12字，英文15-40字符（适合KTV逐句显示）\n");
            prompt.append("2. 在自然的语义断点分段（逗号、顿号、短语边界）\n");
            prompt.append("3. 为每段分配精确的开始和结束时间（毫秒）\n");
            prompt.append("4. 时间分配基于阅读速度：中文约4字/秒，英文约18字符/秒\n");
            prompt.append("5. 段与段之间有100-200ms的小间隔\n");
            prompt.append("6. 确保所有段的时间加起来不超过视频总时长\n");
            prompt.append("7. **重要**：即使文本很短，也要尽量分成至少2-3段\n\n");
            prompt.append("示例（30字文本，6秒视频）：\n");
            prompt.append("输入：\"你敢相信么，在广州，只要499，就能给您爱车安排一套，智能的大屏导航\"\n");
            prompt.append("输出：\n");
            prompt.append("{\n");
            prompt.append("  \"segments\": [\n");
            prompt.append("    {\"startMs\": 0, \"endMs\": 1500, \"text\": \"你敢相信么\"},\n");
            prompt.append("    {\"startMs\": 1700, \"endMs\": 3000, \"text\": \"在广州，只要499\"},\n");
            prompt.append("    {\"startMs\": 3200, \"endMs\": 4500, \"text\": \"就能给您爱车安排一套\"},\n");
            prompt.append("    {\"startMs\": 4700, \"endMs\": 6000, \"text\": \"智能的大屏导航\"}\n");
            prompt.append("  ]\n");
            prompt.append("}\n\n");
            prompt.append("现在请处理上面的文本，输出格式要求：\n");
            prompt.append("1. 仅输出纯JSON，不要markdown代码块（不要```json```）\n");
            prompt.append("2. 不要任何解释或额外文字\n");
            prompt.append("3. 直接以{开始，以}结束\n");
            
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
            log.warn("Error calling Qwen AI (will use fallback): {}", e.getMessage());
            log.debug("Qwen AI error details:", e);
            return null;
        }
    }
    
    /**
     * Fallback method using simple text splitting (when AI fails)
     * Creates smaller segments suitable for KTV-style display
     */
    private List<SubtitleSegment> fallbackSplit(String text, int videoDurationSeconds, long sceneStartTimeMs) {
        List<SubtitleSegment> segments = new ArrayList<>();
        
        // Detect if text is primarily Chinese or English
        boolean isChinese = containsChinese(text);
        // Smaller segments for KTV display
        int maxSegmentLength = isChinese ? 12 : 40;
        double charsPerSecond = isChinese ? 4.0 : 18.0;  // Slightly faster for better pacing
        
        // Split text into segments based on punctuation and max length
        List<String> textSegments = splitTextIntoSegments(text, maxSegmentLength);
        
        if (textSegments.isEmpty()) {
            return segments;
        }
        
        log.info("Fallback split created {} text segments", textSegments.size());
        
        // Calculate timing for each segment with gaps
        long videoDurationMs = videoDurationSeconds * 1000L;
        long currentTimeMs = 0;
        long gapMs = 150; // 150ms gap between segments
        
        for (int i = 0; i < textSegments.size(); i++) {
            String segmentText = textSegments.get(i);
            
            // Calculate duration based on text length and reading speed
            double segmentDurationSeconds = segmentText.length() / charsPerSecond;
            long segmentDurationMs = (long) (segmentDurationSeconds * 1000);
            
            // Add gap before segment (except first one)
            if (i > 0) {
                currentTimeMs += gapMs;
            }
            
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
        
        // Extend last segment to fill remaining time if needed
        if (!segments.isEmpty() && currentTimeMs < videoDurationMs) {
            SubtitleSegment lastSegment = segments.get(segments.size() - 1);
            lastSegment.setEndTimeMs(videoDurationMs + sceneStartTimeMs);
        }
        
        log.info("Fallback split created {} subtitle segments", segments.size());
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
     * Extract content from Qwen API response and strip markdown formatting
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
            if (content == null) {
                return null;
            }
            
            String contentStr = content.toString().trim();
            
            // Strip markdown code blocks if present (```json ... ``` or ``` ... ```)
            if (contentStr.startsWith("```")) {
                // Remove opening ```json or ```
                contentStr = contentStr.replaceFirst("^```(?:json)?\\s*", "");
                // Remove closing ```
                contentStr = contentStr.replaceFirst("```\\s*$", "");
                contentStr = contentStr.trim();
                log.debug("Stripped markdown code blocks from Qwen response");
            }
            
            return contentStr;
            
        } catch (Exception e) {
            log.error("Failed to extract content from response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Split text into segments based on punctuation and max length
     * Creates smaller segments suitable for KTV-style display
     */
    private List<String> splitTextIntoSegments(String text, int maxLength) {
        List<String> segments = new ArrayList<>();
        
        // Split by common punctuation marks (including Chinese punctuation)
        String[] sentences = text.split("[。！？；，、,.!?;]");
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }
            
            // If sentence is longer than max length, split it further
            if (sentence.length() > maxLength) {
                // Split long sentence into chunks
                for (int i = 0; i < sentence.length(); i += maxLength) {
                    int end = Math.min(i + maxLength, sentence.length());
                    String chunk = sentence.substring(i, end).trim();
                    if (!chunk.isEmpty()) {
                        segments.add(chunk);
                    }
                }
            } else {
                // Add sentence as-is if it's within max length
                segments.add(sentence);
            }
        }
        
        // If no segments were created (no punctuation), split by max length
        if (segments.isEmpty() && !text.isEmpty()) {
            for (int i = 0; i < text.length(); i += maxLength) {
                int end = Math.min(i + maxLength, text.length());
                String chunk = text.substring(i, end).trim();
                if (!chunk.isEmpty()) {
                    segments.add(chunk);
                }
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
