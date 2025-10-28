package com.example.demo.ai.services;

import com.example.demo.model.Scene;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen-based Scene Comparison Service
 * 
 * Direct 2-image comparison with purpose-driven evaluation.
 * 
 * Flow:
 * 1. Extract user keyframe from scene video
 * 2. Build context-aware prompt (purpose + key elements + template subtitle)
 * 3. Call Qwen VL with both images directly
 * 4. Return weighted evaluation (Purpose 50% + Key Elements 30% + Visual 20%)
 */
@Service
public class QwenSceneComparisonService {
    
    private static final Logger log = LoggerFactory.getLogger(QwenSceneComparisonService.class);
    
    @Autowired
    private KeyframeExtractionService keyframeExtractionService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${AI_QWEN_ENDPOINT:${qwen.api.base:}}")
    private String qwenApiBase;
    
    @Value("${AI_QWEN_API_KEY:${qwen.api.key:}}")
    private String qwenApiKey;
    
    @Value("${AI_QWEN_MODEL:${qwen.model:qwen-plus}}")
    private String qwenModel;
    
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
    
    /**
     * Direct 2-image comparison with purpose-driven evaluation
     * Skips separate user video analysis - compares images directly with context
     * 
     * @param templateScene Template scene with purpose, key elements, and subtitle
     * @param userVideoUrl User's uploaded scene video URL
     * @param language Language for analysis (zh, en, etc.)
     * @return ComparisonResult with score (0-100) and suggestions
     */
    public ComparisonResult compareWithDirectVL(
        Scene templateScene,
        String userVideoUrl,
        String language
    ) {
        log.info("[DIRECT-COMPARISON] ========================================");
        log.info("[DIRECT-COMPARISON] Starting direct 2-image comparison");
        log.info("[DIRECT-COMPARISON] Template scene: {}", templateScene.getSceneNumber());
        log.info("[DIRECT-COMPARISON] ========================================");
        
        try {
            // Step 1: Extract user keyframe from the scene video
            // User uploads a scene video (not entire video), so extract from middle of uploaded video
            log.info("[DIRECT-COMPARISON] Extracting user keyframe from scene video (1 second in)");
            java.time.Duration keyframeTime = java.time.Duration.ofSeconds(1);  // 1 second into the scene video
            String userKeyframeUrl = keyframeExtractionService.extractKeyframe(userVideoUrl, keyframeTime, null);
            log.info("[DIRECT-COMPARISON] User keyframe: {}", userKeyframeUrl);
            
            // Step 2: Build direct comparison prompt with context (no user subtitle needed)
            log.info("[DIRECT-COMPARISON] Building comparison prompt");
            String prompt = buildDirectComparisonPrompt(templateScene, language);
            
            // Step 3: Call Qwen VL with BOTH images
            log.info("[DIRECT-COMPARISON] Calling Qwen VL with 2 images");
            String result = callQwenVLWithTwoImages(
                templateScene.getKeyframeUrl(),
                userKeyframeUrl,
                prompt
            );
            
            // Step 4: Parse and return result
            log.info("[DIRECT-COMPARISON] Parsing result");
            ComparisonResult comparisonResult = parseDirectComparisonResult(result);
            
            log.info("[DIRECT-COMPARISON] ========================================");
            log.info("[DIRECT-COMPARISON] ✅ Comparison complete");
            log.info("[DIRECT-COMPARISON]   - Overall Score: {}/100", comparisonResult.getSimilarityScore());
            log.info("[DIRECT-COMPARISON]   - Suggestions: {}", comparisonResult.getSuggestions().size());
            log.info("[DIRECT-COMPARISON] ========================================");
            
            return comparisonResult;
            
        } catch (Exception e) {
            log.error("[DIRECT-COMPARISON] ========================================");
            log.error("[DIRECT-COMPARISON] ❌ Error: {}", e.getMessage(), e);
            log.error("[DIRECT-COMPARISON] ========================================");
            return ComparisonResult.error("比较失败: " + e.getMessage());
        }
    }
    
    /**
     * Build prompt for direct 2-image comparison with purpose context
     * Priority: Purpose Match > Key Elements > Visual Similarity
     */
    private String buildDirectComparisonPrompt(Scene templateScene, String language) {
        boolean isChinese = "zh".equalsIgnoreCase(language);
        StringBuilder sb = new StringBuilder();
        
        if (isChinese) {
            sb.append("你是视频营销专家。请对比两张图片（图1是模板，图2是用户拍摄），");
            sb.append("评估用户视频是否符合模板要求。\n\n");
            
            // Context 1: Video Purpose (CRITICAL)
            // Note: Purpose should be passed separately or stored in Scene model
            // For now, comparison focuses on key elements and visual similarity
            
            // Context 2: Scene Key Elements
            if (templateScene.getKeyElements() != null && !templateScene.getKeyElements().isEmpty()) {
                sb.append("【场景关键要素】\n");
                sb.append(String.join("、", templateScene.getKeyElements())).append("\n\n");
            }
            
            // Context 3: Template Subtitle (for reference)
            if (templateScene.getSubtitles() != null && !templateScene.getSubtitles().isEmpty()) {
                String templateSubtitle = templateScene.getSubtitles().stream()
                    .map(com.example.demo.ai.subtitle.SubtitleSegment::getText)
                    .collect(java.util.stream.Collectors.joining(" "));
                sb.append("【模板语音内容（参考）】\n");
                sb.append(templateSubtitle).append("\n\n");
            }
            
            // Evaluation Criteria (weighted, with harsh purpose filtering)
            sb.append("请按以下顺序评估（权重递减）：\n\n");
            
            sb.append("1. 目的匹配度（50分）- 最重要！\n");
            sb.append("   用户图片的内容是否符合视频目的？\n");
            sb.append("   ⚠️ 如果完全不符合（例如：视频目的是推广导航功能，用户却拍摄车衣贴膜），直接给0分！\n");
            sb.append("   只有当图片内容与视频目的相关时，才继续评估其他项。\n\n");
            
            sb.append("2. 关键要素完整度（30分）\n");
            sb.append("   用户图片是否包含场景关键要素？\n");
            sb.append("   对比关键要素列表，检查用户图片中出现了哪些。\n");
            sb.append("   缺少重要要素扣分。\n\n");
            
            sb.append("3. 视觉相似度（20分）\n");
            sb.append("   两张图片的构图、角度、内容是否相似？\n");
            sb.append("   不要求完全一样，但应该是同类型的场景。\n\n");
            
            // Output Format
            sb.append("请返回JSON格式（不要包含其他内容）：\n");
            sb.append("{\n");
            sb.append("  \"overallScore\": 75,\n");
            sb.append("  \"purposeMatch\": {\n");
            sb.append("    \"score\": 45,\n");
            sb.append("    \"matched\": true,\n");
            sb.append("    \"issue\": \"问题描述（如果有）\"\n");
            sb.append("  },\n");
            sb.append("  \"keyElementsMatch\": {\n");
            sb.append("    \"score\": 28,\n");
            sb.append("    \"foundElements\": [\"用户图片中找到的关键要素\"],\n");
            sb.append("    \"missingElements\": [\"缺失的关键要素\"],\n");
            sb.append("    \"issue\": \"问题描述（如果有）\"\n");
            sb.append("  },\n");
            sb.append("  \"visualSimilarity\": {\n");
            sb.append("    \"score\": 18,\n");
            sb.append("    \"similar\": true,\n");
            sb.append("    \"differences\": [\"主要差异点\"]\n");
            sb.append("  },\n");
            sb.append("  \"suggestions\": [\n");
            sb.append("    \"具体改进建议1\",\n");
            sb.append("    \"具体改进建议2\"\n");
            sb.append("  ]\n");
            sb.append("}\n");
        } else {
            // English version
            sb.append("You are a video marketing expert. Compare two images (Image 1 is template, Image 2 is user-recorded), ");
            sb.append("evaluate if user video meets template requirements.\n\n");
            
            // Note: Purpose should be passed separately or stored in Scene model
            // For now, comparison focuses on key elements and visual similarity
            
            if (templateScene.getKeyElements() != null && !templateScene.getKeyElements().isEmpty()) {
                sb.append("【Scene Key Elements】\n");
                sb.append(String.join(", ", templateScene.getKeyElements())).append("\n\n");
            }
            
            if (templateScene.getSubtitles() != null && !templateScene.getSubtitles().isEmpty()) {
                String templateSubtitle = templateScene.getSubtitles().stream()
                    .map(com.example.demo.ai.subtitle.SubtitleSegment::getText)
                    .collect(java.util.stream.Collectors.joining(" "));
                sb.append("【Template Audio (Reference)】\n");
                sb.append(templateSubtitle).append("\n\n");
            }
            
            sb.append("Evaluate in order (weighted):\n\n");
            sb.append("1. Purpose Match (50 points) - CRITICAL!\n");
            sb.append("   Does user image content match video purpose?\n");
            sb.append("   ⚠️ If completely off-topic (e.g., purpose is navigation but shows car wrap), give 0 points!\n\n");
            
            sb.append("2. Key Elements Completeness (30 points)\n");
            sb.append("   Does user image contain key elements?\n\n");
            
            sb.append("3. Visual Similarity (20 points)\n");
            sb.append("   Are composition, angle, content similar?\n\n");
            
            sb.append("Return JSON format only:\n");
            sb.append("{\n");
            sb.append("  \"overallScore\": 90,\n");
            sb.append("  \"purposeMatch\": {\"score\": 45, \"matched\": true, \"issue\": \"\"},\n");
            sb.append("  \"keyElementsMatch\": {\"score\": 28, \"foundElements\": [], \"missingElements\": [], \"issue\": \"\"},\n");
            sb.append("  \"visualSimilarity\": {\"score\": 18, \"similar\": true, \"differences\": []},\n");
            sb.append("  \"suggestions\": []\n");
            sb.append("}\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Call Qwen VL API with two images
     */
    private String callQwenVLWithTwoImages(
        String templateImageUrl,
        String userImageUrl,
        String prompt
    ) throws Exception {
        
        // Build request with 2 images
        Map<String, Object> request = new HashMap<>();
        request.put("model", "qwen-vl-plus");
        
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        
        // Content with 2 images + text
        List<Map<String, Object>> content = new ArrayList<>();
        
        // Image 1: Template
        Map<String, Object> img1 = new HashMap<>();
        img1.put("type", "image_url");
        Map<String, String> img1Url = new HashMap<>();
        img1Url.put("url", templateImageUrl);
        img1.put("image_url", img1Url);
        content.add(img1);
        
        // Image 2: User
        Map<String, Object> img2 = new HashMap<>();
        img2.put("type", "image_url");
        Map<String, String> img2Url = new HashMap<>();
        img2Url.put("url", userImageUrl);
        img2.put("image_url", img2Url);
        content.add(img2);
        
        // Text: Prompt
        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", prompt);
        content.add(text);
        
        message.put("content", content);
        messages.add(message);
        request.put("messages", messages);
        
        // Call API
        String requestJson = objectMapper.writeValueAsString(request);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + qwenApiKey);
        
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestJson, headers);
        
        org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
            qwenApiBase,
            org.springframework.http.HttpMethod.POST,
            entity,
            String.class
        );
        
        // Extract content from response
        String responseContent = extractContent(response.getBody());
        if (responseContent == null) {
            throw new Exception("Could not extract content from Qwen VL response");
        }
        
        return responseContent;
    }
    
    /**
     * Parse direct comparison result from Qwen VL
     */
    private ComparisonResult parseDirectComparisonResult(String jsonResponse) {
        try {
            // Clean JSON (remove code blocks if present)
            String cleanJson = jsonResponse
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();
            
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(cleanJson);
            
            ComparisonResult result = new ComparisonResult();
            
            // Overall score
            if (root.has("overallScore")) {
                result.setSimilarityScore(root.get("overallScore").asInt());
            }
            
            // Extract suggestions
            List<String> suggestions = new ArrayList<>();
            if (root.has("suggestions") && root.get("suggestions").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode suggestion : root.get("suggestions")) {
                    suggestions.add(suggestion.asText());
                }
            }
            result.setSuggestions(suggestions);
            
            // Extract matched/missing objects for backward compatibility
            List<String> matchedObjects = new ArrayList<>();
            List<String> missingObjects = new ArrayList<>();
            
            if (root.has("keyElementsMatch")) {
                com.fasterxml.jackson.databind.JsonNode keyElements = root.get("keyElementsMatch");
                if (keyElements.has("foundElements") && keyElements.get("foundElements").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode element : keyElements.get("foundElements")) {
                        matchedObjects.add(element.asText());
                    }
                }
                if (keyElements.has("missingElements") && keyElements.get("missingElements").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode element : keyElements.get("missingElements")) {
                        missingObjects.add(element.asText());
                    }
                }
            }
            
            result.setMatchedObjects(matchedObjects);
            result.setMissingObjects(missingObjects);
            
            log.info("[DIRECT-COMPARISON] Parsed result: score={}, matched={}, missing={}, suggestions={}", 
                result.getSimilarityScore(), matchedObjects.size(), missingObjects.size(), suggestions.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("[DIRECT-COMPARISON] Failed to parse result: {}", e.getMessage());
            return ComparisonResult.error("解析比较结果失败: " + e.getMessage());
        }
    }
    
    /**
     * Extract content from Qwen API response
     */
    private String extractContent(String responseBody) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseBody);
            com.fasterxml.jackson.databind.JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                com.fasterxml.jackson.databind.JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    com.fasterxml.jackson.databind.JsonNode content = message.get("content");
                    if (content != null) {
                        String text = content.asText();
                        // Strip markdown code fences
                        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
                        return text;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DIRECT-COMPARISON] Failed to extract content: {}", e.getMessage());
        }
        return null;
    }
}
