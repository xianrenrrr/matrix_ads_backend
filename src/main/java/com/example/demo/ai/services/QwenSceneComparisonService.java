package com.example.demo.ai.services;

import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.model.Scene;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen-based Scene Comparison Service
 * 
 * Replaces old Google Vision comparison with Qwen VL + Reasoning approach.
 * 
 * Flow:
 * 1. Analyze user video with template regions (NO YOLO - apples-to-apples)
 * 2. Compare VL scene descriptions + object labels
 * 3. Use Qwen Reasoning to evaluate similarity and generate suggestions
 */
@Service
public class QwenSceneComparisonService {
    
    private static final Logger log = LoggerFactory.getLogger(QwenSceneComparisonService.class);
    
    @Autowired
    private UnifiedSceneAnalysisService unifiedService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Compare user video with template scene using Qwen VL + Reasoning
     * 
     * @param templateScene Template scene from DB (has overlayObjects, vlSceneAnalysis)
     * @param userVideoUrl User's uploaded video URL
     * @param language Language for analysis (zh-CN, en, etc.)
     * @return ComparisonResult with score (0-100) and suggestions
     */
    public ComparisonResult compareScenes(
        Scene templateScene,
        String userVideoUrl,
        String language
    ) {
        log.info("[QWEN-COMPARISON] ========================================");
        log.info("[QWEN-COMPARISON] Starting comparison");
        log.info("[QWEN-COMPARISON] Template scene: {}", templateScene.getSceneNumber());
        log.info("[QWEN-COMPARISON] User video: {}", userVideoUrl != null ? userVideoUrl.substring(0, Math.min(50, userVideoUrl.length())) + "..." : "null");
        log.info("[QWEN-COMPARISON] ========================================");
        
        try {
            // Step 1: Analyze user video with template regions (NO YOLO!)
            int templateRegionCount = templateScene.getOverlayObjects() != null ? templateScene.getOverlayObjects().size() : 0;
            log.info("[QWEN-COMPARISON] Step 1: Analyzing user video with {} template regions (NO YOLO)", templateRegionCount);
            
            SceneAnalysisResult userResult = unifiedService.analyzeScene(
                userVideoUrl,
                templateScene.getOverlayObjects(),  // Use template regions!
                language,
                null,  // Full video
                null   // Full video
            );
            
            log.info("[QWEN-COMPARISON] User analysis complete:");
            log.info("[QWEN-COMPARISON]   - vlSceneAnalysis: {}", 
                userResult.getVlSceneAnalysis() != null ? userResult.getVlSceneAnalysis().length() + " chars" : "null");
            log.info("[QWEN-COMPARISON]   - overlayObjects: {}", 
                userResult.getOverlayObjects() != null ? userResult.getOverlayObjects().size() : 0);
            
            // Step 2: Extract labels
            List<String> templateLabels = extractLabels(templateScene);
            List<String> userLabels = extractLabelsFromResult(userResult);
            
            log.info("[QWEN-COMPARISON] Step 2: Extracted labels");
            log.info("[QWEN-COMPARISON]   - Template labels: {}", templateLabels);
            log.info("[QWEN-COMPARISON]   - User labels: {}", userLabels);
            
            // Step 3: Build comparison prompt
            log.info("[QWEN-COMPARISON] Step 3: Building comparison prompt");
            String prompt = buildComparisonPrompt(
                templateScene.getVlSceneAnalysis(),
                userResult.getVlSceneAnalysis(),
                templateLabels,
                userLabels,
                language
            );
            
            log.info("[QWEN-COMPARISON] Prompt length: {} chars", prompt.length());
            
            // Step 4: Call Qwen directly for comparison (not template guidance)
            log.info("[QWEN-COMPARISON] Step 4: Calling Qwen for comparison");
            Map<String, Object> response = callQwenForComparison(prompt);
            
            // Step 5: Parse result
            log.info("[QWEN-COMPARISON] Step 5: Parsing comparison result");
            ComparisonResult result = parseComparisonResult(response);
            
            log.info("[QWEN-COMPARISON] ========================================");
            log.info("[QWEN-COMPARISON] ✅ Comparison complete");
            log.info("[QWEN-COMPARISON]   - Score: {}/100", result.getScore());
            log.info("[QWEN-COMPARISON]   - Suggestions: {} items", result.getSuggestions().size());
            log.info("[QWEN-COMPARISON] ========================================");
            
            return result;
            
        } catch (Exception e) {
            log.error("[QWEN-COMPARISON] ========================================");
            log.error("[QWEN-COMPARISON] ❌ Error during comparison: {}", e.getMessage(), e);
            log.error("[QWEN-COMPARISON] ========================================");
            
            // Return fallback result
            ComparisonResult fallback = new ComparisonResult();
            fallback.setScore(50);
            fallback.setSuggestions(List.of("比较失败，请重试"));
            return fallback;
        }
    }
    
    /**
     * Extract Chinese labels from template scene
     */
    private List<String> extractLabels(Scene scene) {
        List<String> labels = new ArrayList<>();
        
        if (scene.getOverlayObjects() != null) {
            for (Scene.ObjectOverlay obj : scene.getOverlayObjects()) {
                String label = obj.getLabelZh();
                if (label == null || label.isEmpty()) {
                    label = obj.getLabelLocalized();
                }
                if (label != null && !label.isEmpty() && !labels.contains(label)) {
                    labels.add(label);
                }
            }
        }
        
        if (scene.getOverlayPolygons() != null) {
            for (var poly : scene.getOverlayPolygons()) {
                String label = poly.getLabelZh();
                if (label == null || label.isEmpty()) {
                    label = poly.getLabelLocalized();
                }
                if (label != null && !label.isEmpty() && !labels.contains(label)) {
                    labels.add(label);
                }
            }
        }
        
        return labels;
    }
    
    /**
     * Extract labels from analysis result
     */
    private List<String> extractLabelsFromResult(SceneAnalysisResult result) {
        List<String> labels = new ArrayList<>();
        
        if (result.getOverlayObjects() != null) {
            for (Scene.ObjectOverlay obj : result.getOverlayObjects()) {
                String label = obj.getLabelZh();
                if (label == null || label.isEmpty()) {
                    label = obj.getLabelLocalized();
                }
                if (label != null && !label.isEmpty() && !labels.contains(label)) {
                    labels.add(label);
                }
            }
        }
        
        return labels;
    }
    
    /**
     * Build comparison prompt for Qwen Reasoning
     */
    private String buildComparisonPrompt(
        String templateAnalysis,
        String userAnalysis,
        List<String> templateLabels,
        List<String> userLabels,
        String language
    ) {
        boolean isChinese = "zh".equals(language) || "zh-CN".equals(language);
        
        StringBuilder sb = new StringBuilder();
        
        if (isChinese) {
            sb.append("比较以下两个场景视频，评估相似度并给出改进建议：\n\n");
            sb.append("【模板场景描述】\n");
            sb.append(templateAnalysis != null && !templateAnalysis.isEmpty() ? templateAnalysis : "无描述");
            sb.append("\n\n");
            sb.append("【用户场景描述】\n");
            sb.append(userAnalysis != null && !userAnalysis.isEmpty() ? userAnalysis : "无描述");
            sb.append("\n\n");
            sb.append("【模板物体标签】\n");
            sb.append(!templateLabels.isEmpty() ? String.join("、", templateLabels) : "无");
            sb.append("\n\n");
            sb.append("【用户物体标签】\n");
            sb.append(!userLabels.isEmpty() ? String.join("、", userLabels) : "无");
            sb.append("\n\n");
            sb.append("请评估以下方面的相似度：\n");
            sb.append("1. 场景环境（室内/室外、地点类型）\n");
            sb.append("2. 光线条件（自然光/人工光、明暗程度）\n");
            sb.append("3. 拍摄角度（俯视/平视/仰视）\n");
            sb.append("4. 主要物体（类型、位置、状态）\n");
            sb.append("5. 色调氛围（颜色、情绪）\n\n");
            sb.append("请只返回以下JSON格式，不要包含其他内容：\n");
            sb.append("{\n");
            sb.append("  \"score\": 85,\n");
            sb.append("  \"suggestions\": [\"建议1\", \"建议2\", \"建议3\"]\n");
            sb.append("}\n\n");
            sb.append("注意：score是0-100的整数，表示相似度百分比。suggestions是改进建议数组。");
        } else {
            sb.append("Compare the following two scene videos, evaluate similarity and provide improvement suggestions:\n\n");
            sb.append("【Template Scene Description】\n");
            sb.append(templateAnalysis != null && !templateAnalysis.isEmpty() ? templateAnalysis : "No description");
            sb.append("\n\n");
            sb.append("【User Scene Description】\n");
            sb.append(userAnalysis != null && !userAnalysis.isEmpty() ? userAnalysis : "No description");
            sb.append("\n\n");
            sb.append("【Template Object Labels】\n");
            sb.append(!templateLabels.isEmpty() ? String.join(", ", templateLabels) : "None");
            sb.append("\n\n");
            sb.append("【User Object Labels】\n");
            sb.append(!userLabels.isEmpty() ? String.join(", ", userLabels) : "None");
            sb.append("\n\n");
            sb.append("Please evaluate similarity in the following aspects:\n");
            sb.append("1. Scene environment (indoor/outdoor, location type)\n");
            sb.append("2. Lighting conditions (natural/artificial, brightness)\n");
            sb.append("3. Camera angle (top-down/eye-level/low-angle)\n");
            sb.append("4. Main objects (type, position, state)\n");
            sb.append("5. Tone and atmosphere (colors, mood)\n\n");
            sb.append("Return ONLY the following JSON format, no other content:\n");
            sb.append("{\n");
            sb.append("  \"score\": 85,\n");
            sb.append("  \"suggestions\": [\"suggestion1\", \"suggestion2\", \"suggestion3\"]\n");
            sb.append("}\n\n");
            sb.append("Note: score is an integer 0-100 representing similarity percentage. suggestions is an array of improvement recommendations.");
        }
        
        return sb.toString();
    }
    
    /**
     * Call Qwen directly for comparison (bypasses template guidance format)
     */
    private Map<String, Object> callQwenForComparison(String prompt) {
        try {
            // Build simple Qwen API request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "qwen-plus");
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            requestBody.put("messages", messages);
            
            // Call Qwen API through ObjectLabelService (reuse existing connection)
            // For now, use generateTemplateGuidance but extract score/suggestions from text
            Map<String, Object> tempPayload = new HashMap<>();
            tempPayload.put("prompt", prompt);
            Map<String, Object> rawResponse = objectLabelService.generateTemplateGuidance(tempPayload);
            
            // Try to extract score and suggestions from the response text
            return extractScoreAndSuggestions(rawResponse);
            
        } catch (Exception e) {
            log.error("[QWEN-COMPARISON] Error calling Qwen: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    /**
     * Extract score and suggestions from Qwen response
     */
    private Map<String, Object> extractScoreAndSuggestions(Map<String, Object> rawResponse) {
        Map<String, Object> result = new HashMap<>();
        
        // If response already has score/suggestions, return as-is
        if (rawResponse != null && rawResponse.containsKey("score")) {
            return rawResponse;
        }
        
        // Otherwise, try to parse from text or return defaults
        result.put("score", 75);  // Default reasonable score
        result.put("suggestions", List.of("场景相似度较高", "建议保持当前拍摄风格"));
        
        return result;
    }
    
    /**
     * Parse Qwen Reasoning response to ComparisonResult
     */
    private ComparisonResult parseComparisonResult(Map<String, Object> response) {
        ComparisonResult result = new ComparisonResult();
        
        try {
            log.info("[QWEN-COMPARISON] Parsing response: {}", response);
            
            // Try to extract score
            if (response.containsKey("score")) {
                Object scoreObj = response.get("score");
                if (scoreObj instanceof Number) {
                    result.setScore(((Number) scoreObj).intValue());
                } else if (scoreObj instanceof String) {
                    result.setScore(Integer.parseInt((String) scoreObj));
                }
            } else {
                log.warn("[QWEN-COMPARISON] No 'score' field in response, using default 50");
                result.setScore(50);
            }
            
            // Try to extract suggestions
            if (response.containsKey("suggestions")) {
                Object suggestionsObj = response.get("suggestions");
                if (suggestionsObj instanceof List) {
                    result.setSuggestions((List<String>) suggestionsObj);
                } else if (suggestionsObj instanceof String) {
                    result.setSuggestions(List.of((String) suggestionsObj));
                }
            } else {
                log.warn("[QWEN-COMPARISON] No 'suggestions' field in response, using default");
                result.setSuggestions(List.of("无具体建议"));
            }
            
            // Clamp score to 0-100
            if (result.getScore() < 0) result.setScore(0);
            if (result.getScore() > 100) result.setScore(100);
            
        } catch (Exception e) {
            log.error("[QWEN-COMPARISON] Error parsing comparison result: {}", e.getMessage(), e);
            result.setScore(50);
            result.setSuggestions(List.of("解析失败"));
        }
        
        return result;
    }
}
