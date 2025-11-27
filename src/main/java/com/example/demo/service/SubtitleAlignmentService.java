package com.example.demo.service;

import com.example.demo.ai.subtitle.ASRSubtitleExtractor;
import com.example.demo.ai.subtitle.SubtitleSegment;
import com.example.demo.model.Scene;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Service for aligning subtitles using ASR results and expected scriptLine.
 * 
 * Flow:
 * 1. Get expected scriptLine from template scene
 * 2. Run ASR on user's recorded video to get actual speech with timestamps
 * 3. Use Qwen to align/merge: correct ASR errors using scriptLine as reference
 * 4. Output accurate SubtitleSegments with proper timing from ASR
 * 
 * This produces subtitles that:
 * - Have accurate timing from ASR (what user actually said and when)
 * - Have correct text from scriptLine (fixing ASR recognition errors)
 */
@Service
public class SubtitleAlignmentService {
    
    private static final Logger log = LoggerFactory.getLogger(SubtitleAlignmentService.class);
    
    @Autowired
    private ASRSubtitleExtractor asrExtractor;
    
    @Autowired
    private AlibabaOssStorageService ossStorageService;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${AI_QWEN_ENDPOINT:${qwen.api.base:}}")
    private String qwenApiBase;
    
    @Value("${AI_QWEN_API_KEY:${qwen.api.key:}}")
    private String qwenApiKey;
    
    @Value("${AI_QWEN_MODEL:${qwen.model:qwen-plus}}")
    private String qwenModel;
    
    public SubtitleAlignmentService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Generate aligned subtitles for a scene by combining ASR results with expected scriptLine
     * 
     * @param scene Template scene with scriptLine (expected text)
     * @param userVideoUrl URL of user's recorded video
     * @param sceneStartTimeMs Start time offset for this scene in the final video
     * @return List of aligned subtitle segments with accurate timing
     */
    public List<SubtitleSegment> generateAlignedSubtitles(Scene scene, String userVideoUrl, long sceneStartTimeMs) {
        log.info("=== SUBTITLE ALIGNMENT START ===");
        log.info("Scene: {}, ScriptLine: {}", scene.getSceneNumber(), 
            scene.getScriptLine() != null ? scene.getScriptLine().substring(0, Math.min(50, scene.getScriptLine().length())) + "..." : "null");
        
        String expectedText = scene.getScriptLine();
        if (expectedText == null || expectedText.trim().isEmpty()) {
            log.warn("No scriptLine for scene {}, skipping subtitle alignment", scene.getSceneNumber());
            return new ArrayList<>();
        }
        
        try {
            // Step 1: Run ASR on user's video to get actual speech with timestamps
            log.info("Step 1: Running ASR on user video...");
            List<SubtitleSegment> asrSegments = asrExtractor.extract(userVideoUrl, "zh");
            
            if (asrSegments.isEmpty()) {
                log.warn("ASR returned no segments, falling back to scriptLine-based timing");
                return fallbackToScriptLineTiming(expectedText, scene.getSceneDurationInSeconds(), sceneStartTimeMs);
            }
            
            log.info("ASR returned {} segments", asrSegments.size());
            
            // Step 2: Use Qwen to align ASR results with expected scriptLine
            log.info("Step 2: Aligning ASR with scriptLine using Qwen...");
            List<SubtitleSegment> alignedSegments = alignWithQwen(expectedText, asrSegments, sceneStartTimeMs);
            
            log.info("=== SUBTITLE ALIGNMENT COMPLETE ===");
            log.info("Generated {} aligned subtitle segments", alignedSegments.size());
            
            return alignedSegments;
            
        } catch (Exception e) {
            log.error("Subtitle alignment failed: {}", e.getMessage(), e);
            log.warn("Falling back to scriptLine-based timing");
            return fallbackToScriptLineTiming(expectedText, scene.getSceneDurationInSeconds(), sceneStartTimeMs);
        }
    }
    
    /**
     * Use Qwen to align ASR results with expected scriptLine
     * 
     * The AI will:
     * 1. Match ASR segments to expected text
     * 2. Correct ASR recognition errors using scriptLine as reference
     * 3. Keep accurate timing from ASR
     * 4. Output corrected segments
     */
    private List<SubtitleSegment> alignWithQwen(String expectedText, List<SubtitleSegment> asrSegments, long sceneStartTimeMs) {
        try {
            // Build prompt for Qwen
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是字幕校对专家。请将ASR识别结果与预期文本对齐，修正识别错误。\n\n");
            
            prompt.append("【预期文本（正确内容）】\n");
            prompt.append(expectedText).append("\n\n");
            
            prompt.append("【ASR识别结果（有时间戳但可能有错误）】\n");
            for (int i = 0; i < asrSegments.size(); i++) {
                SubtitleSegment seg = asrSegments.get(i);
                prompt.append(String.format("%d. [%dms-%dms] \"%s\"\n", 
                    i + 1, seg.getStartTimeMs(), seg.getEndTimeMs(), seg.getText()));
            }
            prompt.append("\n");
            
            prompt.append("任务要求：\n");
            prompt.append("1. 保留ASR的时间戳（startMs, endMs）- 这是用户实际说话的时间\n");
            prompt.append("2. 用预期文本修正ASR识别错误（如：\"飞利普\" → \"飞利浦\"）\n");
            prompt.append("3. 如果ASR漏掉了某些词，根据上下文时间插入\n");
            prompt.append("4. 如果ASR多识别了噪音/背景音，删除这些段\n");
            prompt.append("5. 合并过短的相邻段（<500ms），每段保持5-15个字\n");
            prompt.append("6. 确保最终文本与预期文本语义一致\n\n");
            
            prompt.append("输出格式（纯JSON，不要markdown）：\n");
            prompt.append("{\n");
            prompt.append("  \"segments\": [\n");
            prompt.append("    {\"startMs\": 0, \"endMs\": 1500, \"text\": \"修正后的文本\"},\n");
            prompt.append("    {\"startMs\": 1600, \"endMs\": 3000, \"text\": \"修正后的文本\"}\n");
            prompt.append("  ]\n");
            prompt.append("}\n");
            
            // Call Qwen API
            String response = callQwenAPI(prompt.toString());
            
            // Parse response
            return parseAlignmentResponse(response, sceneStartTimeMs);
            
        } catch (Exception e) {
            log.error("Qwen alignment failed: {}", e.getMessage());
            // Fallback: use ASR segments directly with offset
            return applyTimeOffset(asrSegments, sceneStartTimeMs);
        }
    }
    
    /**
     * Call Qwen API for text alignment
     */
    private String callQwenAPI(String prompt) throws Exception {
        if (qwenApiKey == null || qwenApiKey.isBlank()) {
            throw new IllegalStateException("Qwen API key not configured");
        }
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", qwenModel);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        content.add(textContent);
        
        message.put("content", content);
        messages.add(message);
        request.put("messages", messages);
        
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
            throw new RuntimeException("Qwen API returned: " + response.getStatusCodeValue());
        }
        
        return extractContent(response.getBody());
    }
    
    /**
     * Parse alignment response from Qwen
     */
    private List<SubtitleSegment> parseAlignmentResponse(String response, long sceneStartTimeMs) {
        List<SubtitleSegment> segments = new ArrayList<>();
        
        try {
            // Use centralized AI response fixer
            String cleanJson = com.example.demo.ai.util.AIResponseFixer.cleanAndFixJson(response);
            
            if (cleanJson == null) {
                log.error("No JSON found in Qwen alignment response");
                return segments;
            }
            
            Map<String, Object> result = objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {});
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> segmentList = (List<Map<String, Object>>) result.get("segments");
            
            if (segmentList != null) {
                for (Map<String, Object> seg : segmentList) {
                    long startMs = ((Number) seg.get("startMs")).longValue() + sceneStartTimeMs;
                    long endMs = ((Number) seg.get("endMs")).longValue() + sceneStartTimeMs;
                    String text = (String) seg.get("text");
                    
                    if (text != null && !text.trim().isEmpty()) {
                        segments.add(new SubtitleSegment(startMs, endMs, text.trim(), 1.0));
                    }
                }
            }
            
            log.info("Parsed {} aligned segments from Qwen response", segments.size());
            
        } catch (Exception e) {
            log.error("Failed to parse alignment response: {}", e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Apply time offset to ASR segments (fallback when Qwen fails)
     */
    private List<SubtitleSegment> applyTimeOffset(List<SubtitleSegment> segments, long offsetMs) {
        List<SubtitleSegment> result = new ArrayList<>();
        for (SubtitleSegment seg : segments) {
            result.add(new SubtitleSegment(
                seg.getStartTimeMs() + offsetMs,
                seg.getEndTimeMs() + offsetMs,
                seg.getText(),
                seg.getConfidence()
            ));
        }
        return result;
    }
    
    /**
     * Fallback: generate subtitles from scriptLine with estimated timing
     * Used when ASR fails or returns no results
     */
    private List<SubtitleSegment> fallbackToScriptLineTiming(String scriptLine, long durationSeconds, long sceneStartTimeMs) {
        log.info("Using fallback scriptLine timing (no ASR)");
        
        List<SubtitleSegment> segments = new ArrayList<>();
        
        // Simple split by punctuation
        String[] parts = scriptLine.split("[，。！？、,\\.!?]");
        long durationMs = durationSeconds * 1000;
        long timePerPart = durationMs / Math.max(parts.length, 1);
        
        long currentTime = 0;
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                long endTime = Math.min(currentTime + timePerPart, durationMs);
                segments.add(new SubtitleSegment(
                    currentTime + sceneStartTimeMs,
                    endTime + sceneStartTimeMs,
                    part,
                    0.5 // Lower confidence for estimated timing
                ));
                currentTime = endTime + 100; // Small gap
            }
        }
        
        return segments;
    }
    
    /**
     * Normalize Qwen API endpoint
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
            log.error("Failed to extract content: {}", e.getMessage());
            return null;
        }
    }
}
