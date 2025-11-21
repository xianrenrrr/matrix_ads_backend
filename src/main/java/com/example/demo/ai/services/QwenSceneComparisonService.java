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
    
    @Autowired
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
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
     * @param userThumbnailUrl User's thumbnail URL (if already extracted during upload)
     * @param language Language for analysis (zh, en, etc.)
     * @return ComparisonResult with score (0-100) and suggestions
     */
    public ComparisonResult compareWithDirectVL(
        Scene templateScene,
        String userVideoUrl,
        String userThumbnailUrl,
        String language
    ) {
        log.info("[DIRECT-COMPARISON] ========================================");
        log.info("[DIRECT-COMPARISON] Starting direct 2-image comparison");
        log.info("[DIRECT-COMPARISON] Template scene: {}", templateScene.getSceneNumber());
        log.info("[DIRECT-COMPARISON] ========================================");
        
        try {
            // Step 1: Get user keyframe (use thumbnail if available, otherwise extract)
            String userKeyframeUrl;
            if (userThumbnailUrl != null && !userThumbnailUrl.isEmpty()) {
                // Use existing thumbnail (already extracted during upload) - FAST!
                log.info("[DIRECT-COMPARISON] Using existing thumbnail (no extraction needed)");
                userKeyframeUrl = userThumbnailUrl;
                log.info("[DIRECT-COMPARISON] User thumbnail: {}", userKeyframeUrl);
            } else {
                // Fallback: Extract keyframe from video if thumbnail not available
                log.info("[DIRECT-COMPARISON] No thumbnail available, extracting keyframe from scene video (1 second in)");
                java.time.Duration keyframeTime = java.time.Duration.ofSeconds(1);
                userKeyframeUrl = keyframeExtractionService.extractKeyframe(userVideoUrl, keyframeTime, null);
                log.info("[DIRECT-COMPARISON] User keyframe extracted: {}", userKeyframeUrl);
            }
            
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
            if (templateScene.getKeyElementsWithBoxes() != null && !templateScene.getKeyElementsWithBoxes().isEmpty()) {
                sb.append("【场景关键要素】\n");
                List<String> elementNames = templateScene.getKeyElementsWithBoxes().stream()
                    .map(com.example.demo.model.Scene.KeyElement::getName)
                    .collect(java.util.stream.Collectors.toList());
                sb.append(String.join("、", elementNames)).append("\n\n");
            }
            
            // Context 3: Template ScriptLine (for reference)
            if (templateScene.getScriptLine() != null && !templateScene.getScriptLine().isEmpty()) {
                sb.append("【模板语音内容（参考）】\n");
                sb.append(templateScene.getScriptLine()).append("\n\n");
            }
            
            // Evaluation Criteria (STRICT - purpose and key elements must match)
            sb.append("请按以下顺序评估（严格模式 - 不符合要求直接0分）：\n\n");
            
            sb.append("1. 目的匹配度（50分）- 必须匹配！\n");
            sb.append("   用户图片的内容是否符合视频目的？\n");
            sb.append("   ⚠️⚠️⚠️ 如果目的完全不符合，整体分数直接给0分！⚠️⚠️⚠️\n");
            sb.append("   例如：\n");
            sb.append("   - 视频目的是推广导航功能，用户却拍摄车衣贴膜 → overallScore = 0\n");
            sb.append("   - 视频目的是展示餐厅菜品，用户却拍摄店面外观 → overallScore = 0\n");
            sb.append("   在suggestions中明确说明：\"您拍摄的内容与要求不符。请重新拍摄，确保视频中包含[具体要求的内容]。\"\n\n");
            
            sb.append("2. 关键要素完整度（30分）- 必须包含！\n");
            sb.append("   用户图片是否包含所有场景关键要素？\n");
            sb.append("   ⚠️⚠️⚠️ 如果关键要素缺失超过50%，整体分数直接给0分！⚠️⚠️⚠️\n");
            sb.append("   例如：\n");
            sb.append("   - 要求包含[导航屏幕、中控台、CarPlay界面]，用户只拍了方向盘 → overallScore = 0\n");
            sb.append("   - 要求包含[菜品、餐具、用餐环境]，用户只拍了桌子 → overallScore = 0\n");
            sb.append("   对比关键要素列表，检查用户图片中出现了哪些。\n");
            sb.append("   在suggestions中明确列出：\"缺少关键要素：[缺失的要素列表]。请重新拍摄，确保包含所有要求的内容。\"\n\n");
            
            sb.append("3. 视觉相似度（20分）\n");
            sb.append("   两张图片的构图、角度、内容是否相似？\n");
            sb.append("   不要求完全一样，但应该是同类型的场景。\n");
            sb.append("   如果前两项都通过，这一项才有意义。\n\n");
            
            sb.append("⚠️⚠️⚠️ 重要评分规则 ⚠️⚠️⚠️\n");
            sb.append("- 如果目的不匹配 → overallScore = 0\n");
            sb.append("- 如果关键要素缺失超过50% → overallScore = 0\n");
            sb.append("- 只有当目的匹配且关键要素基本齐全时，才能给及格分（60分以上）\n\n");
            
            // Output Format - CRITICAL: JSON ONLY!
            sb.append("\n⚠️⚠️⚠️ 重要：必须只返回JSON格式，不要包含任何解释文字！⚠️⚠️⚠️\n\n");
            sb.append("返回格式（纯JSON，不要有任何其他文字）：\n");
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
            sb.append("}\n\n");
            sb.append("⚠️ 再次提醒：只返回上面的JSON，不要有\"图1\"、\"图2\"等描述文字！\n");
        } else {
            // English version
            sb.append("You are a video marketing expert. Compare two images (Image 1 is template, Image 2 is user-recorded), ");
            sb.append("evaluate if user video meets template requirements.\n\n");
            
            // Note: Purpose should be passed separately or stored in Scene model
            // For now, comparison focuses on key elements and visual similarity
            
            if (templateScene.getKeyElementsWithBoxes() != null && !templateScene.getKeyElementsWithBoxes().isEmpty()) {
                sb.append("【Scene Key Elements】\n");
                List<String> elementNames = templateScene.getKeyElementsWithBoxes().stream()
                    .map(com.example.demo.model.Scene.KeyElement::getName)
                    .collect(java.util.stream.Collectors.toList());
                sb.append(String.join(", ", elementNames)).append("\n\n");
            }
            
            if (templateScene.getScriptLine() != null && !templateScene.getScriptLine().isEmpty()) {
                sb.append("【Template Audio (Reference)】\n");
                sb.append(templateScene.getScriptLine()).append("\n\n");
            }
            
            sb.append("Evaluate in order (STRICT MODE - 0 points if requirements not met):\n\n");
            sb.append("1. Purpose Match (50 points) - CRITICAL!\n");
            sb.append("   Does user image content match video purpose?\n");
            sb.append("   ⚠️⚠️⚠️ If completely off-topic, give overallScore = 0! ⚠️⚠️⚠️\n");
            sb.append("   Examples:\n");
            sb.append("   - Purpose is navigation, user shows car wrap → overallScore = 0\n");
            sb.append("   - Purpose is food, user shows exterior → overallScore = 0\n\n");
            
            sb.append("2. Key Elements Completeness (30 points) - MUST INCLUDE!\n");
            sb.append("   Does user image contain all key elements?\n");
            sb.append("   ⚠️⚠️⚠️ If missing >50% of key elements, give overallScore = 0! ⚠️⚠️⚠️\n");
            sb.append("   List found and missing elements in response.\n\n");
            
            sb.append("3. Visual Similarity (20 points)\n");
            sb.append("   Are composition, angle, content similar?\n");
            sb.append("   Only matters if first two criteria pass.\n\n");
            
            sb.append("⚠️⚠️⚠️ SCORING RULES ⚠️⚠️⚠️\n");
            sb.append("- Purpose mismatch → overallScore = 0\n");
            sb.append("- Missing >50% key elements → overallScore = 0\n");
            sb.append("- Only give passing score (60+) if purpose matches AND key elements present\n\n");
            
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
        
        // Image 1: Template (convert to data URL to avoid download timeout)
        Map<String, Object> img1 = new HashMap<>();
        img1.put("type", "image_url");
        Map<String, String> img1Url = new HashMap<>();
        String templateDataUrl = toDataUrlSafe(templateImageUrl);
        img1Url.put("url", templateDataUrl);
        img1.put("image_url", img1Url);
        content.add(img1);
        
        // Image 2: User (convert to data URL to avoid download timeout)
        Map<String, Object> img2 = new HashMap<>();
        img2.put("type", "image_url");
        Map<String, String> img2Url = new HashMap<>();
        String userDataUrl = toDataUrlSafe(userImageUrl);
        img2Url.put("url", userDataUrl);
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
        
        // Normalize endpoint to ensure it ends with /chat/completions
        String endpoint = normalizeChatEndpoint(qwenApiBase);
        log.info("[DIRECT-COMPARISON] Calling Qwen API endpoint: {}", endpoint);
        
        org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
            endpoint,
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
            
            // Try to extract JSON if it's embedded in text
            if (!cleanJson.startsWith("{")) {
                log.warn("[DIRECT-COMPARISON] Response doesn't start with JSON, attempting to extract...");
                int jsonStart = cleanJson.indexOf("{");
                int jsonEnd = cleanJson.lastIndexOf("}");
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
                    log.info("[DIRECT-COMPARISON] Extracted JSON from position {} to {}", jsonStart, jsonEnd);
                } else {
                    log.error("[DIRECT-COMPARISON] No JSON found in response, using fallback parsing");
                    return parsePlainTextFallback(jsonResponse);
                }
            }
            
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
     * Fallback parser for plain text responses (when Qwen doesn't return JSON)
     */
    private ComparisonResult parsePlainTextFallback(String plainText) {
        log.warn("[DIRECT-COMPARISON] Using fallback plain text parser");
        
        ComparisonResult result = new ComparisonResult();
        result.setSimilarityScore(50); // Default middle score
        
        List<String> suggestions = new ArrayList<>();
        suggestions.add("AI返回了非JSON格式的响应，请重试。");
        suggestions.add("原始响应: " + (plainText.length() > 200 ? plainText.substring(0, 200) + "..." : plainText));
        
        result.setSuggestions(suggestions);
        result.setMatchedObjects(new ArrayList<>());
        result.setMissingObjects(new ArrayList<>());
        
        log.info("[DIRECT-COMPARISON] Fallback result created with score=50");
        return result;
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
    
    /**
     * Normalize Qwen API endpoint to ensure it ends with /chat/completions
     */
    private String normalizeChatEndpoint(String base) {
        String def = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        if (base == null || base.isBlank()) return def;

        if (base.startsWith("http")) {
            if (base.endsWith("/chat/completions")) return base;
            if (base.contains("/compatible-mode/") && base.endsWith("/v1")) return base + "/chat/completions";
            try {
                java.net.URI u = java.net.URI.create(base);
                if (u.getHost() != null && u.getHost().contains("dashscope.aliyuncs.com")) {
                    return def;
                }
            } catch (Exception ignored) {}
            return base; // assume already a full chat URL
        }
        return def;
    }
    
    /**
     * Convert remote image URL to data:image/jpeg;base64,.... If download fails, return original URL.
     * Uses OSS service for authenticated downloads.
     */
    private String toDataUrlSafe(String imageUrl) {
        try {
            // Use OSS service for authenticated download
            byte[] imageBytes = ossStorageService.downloadToByteArray(imageUrl);
            String b64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            return "data:image/jpeg;base64," + b64;
        } catch (Exception e) {
            log.warn("[DIRECT-COMPARISON] Failed to convert image to data URL: {}", e.getMessage());
            // Fallback to original URL if we cannot embed
            return imageUrl;
        }
    }
}
