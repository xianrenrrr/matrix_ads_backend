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
            
            // Step 4: Call Qwen Reasoning
            log.info("[QWEN-COMPARISON] Step 4: Calling Qwen Reasoning for comparison");
            Map<String, Object> payload = new HashMap<>();
            payload.put("prompt", prompt);
            payload.put("language", language);
            payload.put("model", "qwen-plus");  // Use reasoning model
            
            Map<String, Object> response = objectLabelService.generateTemplateGuidance(payload);
            
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
            sb.append("返回JSON格式：\n");
            sb.append("{\n");
            sb.append("  \"score\": 85,\n");
            sb.append("  \"suggestions\": [\"建议1\", \"建议2\", \"建议3\"]\n");
            sb.append("}");
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
            sb.append("Return JSON format:\n");
            sb.append("{\n");
            sb.append("  \"score\": 85,\n");
            sb.append("  \"suggestions\": [\"suggestion1\", \"suggestion2\", \"suggestion3\"]\n");
            sb.append("}");
        }
        
        return sb.toString();
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
