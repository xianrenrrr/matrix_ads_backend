package com.example.demo.ai.label.qwen;

import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.ai.util.LabelCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
// no custom request factory to avoid Spring version API mismatch

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class QwenVLPlusLabeler implements ObjectLabelService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LabelCache labelCache;
    
    @Value("${AI_QWEN_ENDPOINT:${qwen.api.base:}}")
    private String qwenApiBase;
    
    @Value("${AI_QWEN_API_KEY:${qwen.api.key:}}")
    private String qwenApiKey;
    
    @Value("${AI_QWEN_MODEL:${qwen.model:qwen-vl-plus}}")
    private String qwenModel;
    
    @Value("${qwen.timeout.ms:15000}")
    private int qwenTimeout;
    
    private static final Pattern CHINESE_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5]{1,4}$");
    private static final String DEFAULT_LABEL = "未知";
    
    public QwenVLPlusLabeler() {
        // Configure RestTemplate with timeouts
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10 seconds connect timeout
        factory.setReadTimeout(60000);     // 60 seconds read timeout (VL can be slow)
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.labelCache = new LabelCache(256);
    }

    @Override
    public String cleanSingleScriptLine(List<Map<String, Object>> asrSegments, String videoDescription, String sceneDescription) {
        try {
            if (asrSegments == null || asrSegments.isEmpty()) {
                return "";
            }

            // Build simple prompt for single scene
            StringBuilder sb = new StringBuilder();
            sb.append("你是语音文本清理助手。请清理这段语音识别文本。\n\n");
            
            // Add context
            if (videoDescription != null && !videoDescription.isEmpty()) {
                sb.append("【视频描述】\n").append(videoDescription).append("\n\n");
            }
            if (sceneDescription != null && !sceneDescription.isEmpty()) {
                sb.append("【场景描述】\n").append(sceneDescription).append("\n\n");
            }
            
            // Add ASR text
            sb.append("【语音识别原始文本】\n");
            for (Map<String, Object> seg : asrSegments) {
                sb.append(seg.get("text")).append(" ");
            }
            sb.append("\n\n");
            
            sb.append("任务：\n")
              .append("1. 结合视频和场景描述理解内容\n")
              .append("2. 清理ASR识别错误（背景音乐、口音、噪音等）\n")
              .append("3. 移除特殊标记如 <|BGM|>、<|/BGM|> 等\n")
              .append("4. 合并文本片段，形成完整、合理的句子\n")
              .append("5. 如果没有有效语音内容，返回空字符串\n\n")
              .append("只返回清理后的文本，不要任何解释或JSON格式。");

            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", sb.toString());
            content.add(textContent);
            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String endpoint = normalizeChatEndpoint(qwenApiBase);
            ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                entity,
                String.class
            );
            String body = response.getBody();
            System.out.println("[QWEN] cleanSingleScriptLine status=" + response.getStatusCodeValue());
            
            if (!response.getStatusCode().is2xxSuccessful()) return "";
            String contentStr = extractContent(body);
            if (contentStr == null || contentStr.isBlank()) return "";

            return contentStr.trim();
        } catch (Exception e) {
            System.err.println("[QWEN] cleanSingleScriptLine failed: " + e.getMessage());
            return "";
        }
    }

    @Override
    public Map<String, Object> cleanScriptLines(List<Map<String, Object>> asrSegments, List<Map<String, Object>> scenes) {
        System.out.println("[QWEN] cleanScriptLines: " + asrSegments.size() + " ASR segments, " + scenes.size() + " scenes");
        
        try {
            if (asrSegments == null || asrSegments.isEmpty() || scenes == null || scenes.isEmpty()) {
                return null;
            }

            // Step 1: Group ASR segments by scene based on timing
            List<List<Map<String, Object>>> asrByScene = new ArrayList<>();
            for (int i = 0; i < scenes.size(); i++) {
                asrByScene.add(new ArrayList<>());
            }
            
            for (Map<String, Object> asr : asrSegments) {
                long asrStart = ((Number) asr.get("startMs")).longValue();
                long asrEnd = ((Number) asr.get("endMs")).longValue();
                long asrMid = (asrStart + asrEnd) / 2;
                
                // Find which scene this ASR belongs to (based on midpoint)
                for (int i = 0; i < scenes.size(); i++) {
                    Map<String, Object> scene = scenes.get(i);
                    long sceneStart = ((Number) scene.get("startMs")).longValue();
                    long sceneEnd = ((Number) scene.get("endMs")).longValue();
                    
                    if (asrMid >= sceneStart && asrMid < sceneEnd) {
                        asrByScene.get(i).add(asr);
                        break;
                    }
                }
            }
            
            // Step 2: Build prompt with scene-grouped ASR
            StringBuilder sb = new StringBuilder();
            sb.append("你是语音文本清理助手。我会给你每个场景的ASR识别文本，请清理并返回需要修改的场景。\n\n");
            
            // Add video description if available
            Object videoDesc = scenes.get(0).get("videoDescription");
            if (videoDesc != null && !videoDesc.toString().isEmpty()) {
                sb.append("【视频描述】\n").append(videoDesc).append("\n\n");
            }
            
            sb.append("【各场景的ASR文本】\n");
            for (int i = 0; i < scenes.size(); i++) {
                List<Map<String, Object>> sceneAsr = asrByScene.get(i);
                sb.append("\n场景").append(i + 1).append("：\n");
                if (sceneAsr.isEmpty()) {
                    sb.append("  (无语音)\n");
                } else {
                    for (Map<String, Object> asr : sceneAsr) {
                        sb.append("  ").append(asr.get("text")).append("\n");
                    }
                }
            }
            
            sb.append("\n任务：\n")
              .append("1. 清理ASR识别错误（背景音乐、口音、噪音等导致的错误文本）\n")
              .append("2. 移除特殊标记如 <|BGM|>、<|/BGM|> 等\n")
              .append("3. 合并同一场景内的文本片段，形成完整、合理的句子\n")
              .append("4. **重要**：只返回需要修改的场景，如果某个场景的ASR文本已经很好，不需要返回\n")
              .append("5. 如果某个场景没有语音内容或全是噪音，返回空字符串\n\n")
              .append("输出格式（仅 JSON，不要任何解释）：\n")
              .append("{\n")
              .append("  \"corrections\": [\n")
              .append("    {\"sceneNumber\": 2, \"scriptLine\": \"清理后的文本\"},\n")
              .append("    {\"sceneNumber\": 5, \"scriptLine\": \"另一个需要修改的场景\"}\n")
              .append("  ]\n")
              .append("}\n\n")
              .append("示例：如果场景1、3、4的ASR文本都很好，只有场景2和5需要修改，就只返回这两个场景的修正。\n")
              .append("如果所有场景都不需要修改，返回空数组：{\"corrections\": []}\n");

            String promptText = sb.toString();

            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", promptText);
            content.add(textContent);
            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String endpoint = normalizeChatEndpoint(qwenApiBase);
            
            ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                entity,
                String.class
            );
            String body = response.getBody();
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                System.err.println("[QWEN] cleanScriptLines failed: " + response.getStatusCodeValue());
                return null;
            }
            
            String contentStr = extractContent(body);
            
            if (contentStr == null || contentStr.isBlank()) {
                System.err.println("[QWEN] cleanScriptLines: empty response");
                return null;
            }

            // Parse JSON object
            Map<String, Object> aiResult = objectMapper.readValue(contentStr, new TypeReference<Map<String,Object>>(){});
            
            // Build scriptLines array with original ASR text as default
            List<String> scriptLines = new ArrayList<>();
            for (int i = 0; i < scenes.size(); i++) {
                List<Map<String, Object>> sceneAsr = asrByScene.get(i);
                if (sceneAsr.isEmpty()) {
                    scriptLines.add("");
                } else {
                    // Concatenate raw ASR as default
                    StringBuilder rawText = new StringBuilder();
                    for (Map<String, Object> asr : sceneAsr) {
                        rawText.append(asr.get("text")).append(" ");
                    }
                    scriptLines.add(rawText.toString().trim());
                }
            }
            
            // Apply corrections from AI
            if (aiResult != null && aiResult.containsKey("corrections")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> corrections = (List<Map<String, Object>>) aiResult.get("corrections");
                System.out.println("[QWEN] Applying " + corrections.size() + " corrections");
                
                for (Map<String, Object> correction : corrections) {
                    int sceneNum = ((Number) correction.get("sceneNumber")).intValue();
                    String correctedText = (String) correction.get("scriptLine");
                    
                    if (sceneNum >= 1 && sceneNum <= scriptLines.size()) {
                        int index = sceneNum - 1;
                        scriptLines.set(index, correctedText);
                        System.out.println("[QWEN] Scene " + sceneNum + " corrected: \"" + 
                            (correctedText.length() > 60 ? correctedText.substring(0, 60) + "..." : correctedText) + "\"");
                    }
                }
            }
            
            // Build result
            Map<String, Object> result = new HashMap<>();
            result.put("scriptLines", scriptLines);
            
            System.out.println("[QWEN] cleanScriptLines completed: " + scriptLines.size() + " scenes");
            
            return result;
        } catch (Exception e) {
            System.err.println("[QWEN] cleanScriptLines error: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> generateTemplateGuidance(Map<String, Object> payload) {
        try {
            if (payload == null || payload.isEmpty()) return null;

            // Build strict Chinese prompt for JSON-only output
            String payloadJson = objectMapper.writeValueAsString(payload);
            StringBuilder sb = new StringBuilder();
            sb.append("你是视频画面理解助手。请基于整段视频信息，生成中文模板“基本信息”和每个场景指导，严格使用JSON输出，不要任何解释文字。\n")
              .append("如果输入中存在 template.userDescription，请参考该描述进行撰写。\n")

              .append("\n");
            
            sb.append("输入（JSON）：\n")
              .append(payloadJson).append("\n")
              .append("输出格式（仅 JSON）：\n")
              .append("{\n")
              .append("  \"template\": {\n")
              .append("    \"videoPurpose\": \"...\",\n")
              .append("    \"tone\": \"...\",\n")
              .append("    \"lightingRequirements\": \"...\",\n")
              .append("    \"backgroundMusic\": \"...\"\n")
              .append("  },\n")
              .append("  \"scenes\": [\n")
              .append("    {\n")
              .append("      \"sceneNumber\": 1,\n")

              .append("      \"presenceOfPerson\": false,\n")
              .append("      \"movementInstructions\": \"...\",\n")
              .append("      \"backgroundInstructions\": \"...\",\n")
              .append("      \"specificCameraInstructions\": \"...\",\n")
              .append("      \"audioNotes\": \"...\"\n")
              .append("    }\n")
              .append("  ]\n")
              .append("}\n");

            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", sb.toString());
            content.add(textContent);
            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String endpointG = normalizeChatEndpoint(qwenApiBase);
            ResponseEntity<String> response = restTemplate.exchange(
                endpointG,
                HttpMethod.POST,
                entity,
                String.class
            );
            String bodyG = response.getBody();
            System.out.println("[QWEN] guidance host=" + safeHost(endpointG) + " model=" + qwenModel +
                " status=" + response.getStatusCodeValue() + " bodyLen=" + (bodyG == null ? 0 : bodyG.length()));
            if (bodyG != null) {
                String head = bodyG.substring(0, Math.min(200, bodyG.length()));
                System.out.println("[QWEN] guidance body head=" + head.replaceAll("\n", " "));
            }
            if (!response.getStatusCode().is2xxSuccessful()) return null;
            String contentStr = extractContent(bodyG);
            if (contentStr == null || contentStr.isBlank()) return null;

            // Parse JSON object
            return objectMapper.readValue(contentStr, new TypeReference<Map<String,Object>>(){});
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Enhanced labelRegions with scriptLine context (optional reference)
     * This is called by UnifiedSceneAnalysisService when scriptLine is available
     */
    @Override
    public Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale, String scriptLineContext) {
        System.out.println("[QWEN] ========================================");
        System.out.println("[QWEN] labelRegions called (4-param WITH SCRIPTLINE)");
        System.out.println("[QWEN] keyframeUrl: " + (keyframeUrl != null ? keyframeUrl.substring(0, Math.min(100, keyframeUrl.length())) + "..." : "null"));
        System.out.println("[QWEN] regions count: " + (regions != null ? regions.size() : 0));
        System.out.println("[QWEN] locale: " + locale);
        System.out.println("[QWEN] scriptLineContext: " + (scriptLineContext != null && !scriptLineContext.isEmpty() ? 
            "\"" + scriptLineContext.substring(0, Math.min(50, scriptLineContext.length())) + (scriptLineContext.length() > 50 ? "...\"" : "\"") : "null"));
        System.out.println("[QWEN] ========================================");
        
        // Call the internal implementation with scriptLine context
        return labelRegionsInternal(keyframeUrl, regions, locale, scriptLineContext, null);
    }
    
    /**
     * Enhanced labelRegions with Azure object hints for targeted grounding
     * This is the most advanced version with both scriptLine and Azure hints
     */
    @Override
    public Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale, String scriptLineContext, List<String> azureObjectHints) {
        System.out.println("[QWEN] ========================================");
        System.out.println("[QWEN] labelRegions called (5-param WITH AZURE HINTS)");
        System.out.println("[QWEN] keyframeUrl: " + (keyframeUrl != null ? keyframeUrl.substring(0, Math.min(100, keyframeUrl.length())) + "..." : "null"));
        System.out.println("[QWEN] regions count: " + (regions != null ? regions.size() : 0));
        System.out.println("[QWEN] locale: " + locale);
        System.out.println("[QWEN] scriptLineContext: " + (scriptLineContext != null && !scriptLineContext.isEmpty() ? 
            "\"" + scriptLineContext.substring(0, Math.min(50, scriptLineContext.length())) + (scriptLineContext.length() > 50 ? "...\"" : "\"") : "null"));
        System.out.println("[QWEN] azureObjectHints: " + (azureObjectHints != null ? azureObjectHints : "null"));
        System.out.println("[QWEN] ========================================");
        
        // Call the internal implementation with both scriptLine and Azure hints (no combined scriptLines)
        return labelRegionsInternal(keyframeUrl, regions, locale, scriptLineContext, azureObjectHints, null);
    }
    
    /**
     * Enhanced 6-parameter version with combined scriptLines from all scenes
     */
    @Override
    public Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale, String scriptLineContext, List<String> azureObjectHints, String combinedScriptLines) {
        System.out.println("[QWEN] ========================================");
        System.out.println("[QWEN] labelRegions called (6-param WITH COMBINED SCRIPTLINES)");
        System.out.println("[QWEN] keyframeUrl: " + (keyframeUrl != null ? keyframeUrl.substring(0, Math.min(100, keyframeUrl.length())) + "..." : "null"));
        System.out.println("[QWEN] regions count: " + (regions != null ? regions.size() : 0));
        System.out.println("[QWEN] locale: " + locale);
        System.out.println("[QWEN] scriptLineContext (this scene): " + (scriptLineContext != null && !scriptLineContext.isEmpty() ? 
            "\"" + scriptLineContext.substring(0, Math.min(50, scriptLineContext.length())) + (scriptLineContext.length() > 50 ? "...\"" : "\"") : "null"));
        System.out.println("[QWEN] combinedScriptLines (all scenes): " + (combinedScriptLines != null && !combinedScriptLines.isEmpty() ? 
            "\"" + combinedScriptLines.substring(0, Math.min(100, combinedScriptLines.length())) + (combinedScriptLines.length() > 100 ? "...\"" : "\"") : "null"));
        System.out.println("[QWEN] azureObjectHints: " + (azureObjectHints != null ? azureObjectHints : "null"));
        System.out.println("[QWEN] ========================================");
        
        // Call the internal implementation with all context
        return labelRegionsInternal(keyframeUrl, regions, locale, scriptLineContext, azureObjectHints, combinedScriptLines);
    }
    
    /**
     * Backward compatible 3-parameter version (no scriptLine context)
     */
    @Override
    public Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale) {
        System.out.println("[QWEN] ========================================");
        System.out.println("[QWEN] labelRegions called (3-param NO SCRIPTLINE)");
        System.out.println("[QWEN] keyframeUrl: " + (keyframeUrl != null ? keyframeUrl.substring(0, Math.min(100, keyframeUrl.length())) + "..." : "null"));
        System.out.println("[QWEN] regions count: " + (regions != null ? regions.size() : 0));
        System.out.println("[QWEN] locale: " + locale);
        System.out.println("[QWEN] ========================================");
        
        // Call the internal implementation without scriptLine context or Azure hints
        return labelRegionsInternal(keyframeUrl, regions, locale, null, null, null);
    }
    
    /**
     * Internal implementation that handles scriptLine context, Azure object hints, and combined scriptLines
     */
    private Map<String, LabelResult> labelRegionsInternal(String keyframeUrl, List<RegionBox> regions, String locale, String scriptLineContext, List<String> azureObjectHints, String combinedScriptLines) {
        
        System.out.println("[QWEN-KEYELEMENTS] 🎯 Starting Qwen VL call for keyElements extraction");
        System.out.println("[QWEN-KEYELEMENTS]    Keyframe: " + (keyframeUrl != null ? keyframeUrl.substring(0, Math.min(80, keyframeUrl.length())) + "..." : "null"));
        System.out.println("[QWEN-KEYELEMENTS]    Regions: " + (regions != null ? regions.size() : 0));
        System.out.println("[QWEN-KEYELEMENTS]    Locale: " + locale);
        System.out.println("[QWEN-KEYELEMENTS]    ScriptLine provided: " + (scriptLineContext != null && !scriptLineContext.isEmpty() ? "YES" : "NO"));
        
        Map<String, LabelResult> out = new HashMap<>();
        if (regions == null || regions.isEmpty() || keyframeUrl == null || keyframeUrl.isBlank()) {
            System.err.println("[QWEN] ❌ Early return - regions or keyframeUrl is null/empty");
            return out;
        }
        try {
            // Build enhanced prompt with scene analysis and key elements
            String regionsJson = objectMapper.writeValueAsString(regions);
            StringBuilder sb = new StringBuilder();
            sb.append("分析这个场景图片，完成三个任务：\n\n");
            
            // Add combined scriptLines context if available (for manual templates with multiple scenes)
            if (combinedScriptLines != null && !combinedScriptLines.isEmpty()) {
                sb.append("【完整模板背景（所有场景的台词）】\n");
                sb.append(combinedScriptLines).append("\n\n");
                System.out.println("[QWEN-COMBINED] ✅ Combined scriptLines context included: \"" + 
                    (combinedScriptLines.length() > 100 ? combinedScriptLines.substring(0, 100) + "..." : combinedScriptLines) + "\"");
            } else {
                System.out.println("[QWEN-COMBINED] ⚠️ No combined scriptLines context");
            }
            
            // Add scriptLine context if available (optional reference)
            if (scriptLineContext != null && !scriptLineContext.isEmpty()) {
                sb.append("【场景语音内容（参考）】\n");
                sb.append(scriptLineContext).append("\n\n");
                System.out.println("[QWEN-KEYELEMENTS] ✅ ScriptLine context included: \"" + 
                    (scriptLineContext.length() > 100 ? scriptLineContext.substring(0, 100) + "..." : scriptLineContext) + "\"");
            } else {
                System.out.println("[QWEN-KEYELEMENTS] ⚠️ No scriptLine context provided");
            }
            
            // Task 1: Different prompts based on whether Azure hints are available
            if (azureObjectHints != null && !azureObjectHints.isEmpty()) {
                // WITH Azure hints - targeted grounding
                sb.append("【Azure检测到的物体】\n");
                for (String hint : azureObjectHints) {
                    sb.append("- ").append(hint).append("\n");
                }
                sb.append("\n");
                
                sb.append("任务1：定位Azure检测到的物体并提供精确边界框\n")
                .append("请在图像中找到上述Azure检测到的物体，为每个物体提供：\n")
                .append("- id: 使用格式 \"obj1\", \"obj2\", \"obj3\" 等\n")
                .append("- labelZh: 简体中文标签（≤4字）\n")
                .append("- conf: 置信度（0~1）\n")
                .append("- box: 精确边界框 [x, y, width, height]，坐标范围 0-1000\n")
                .append("注意：优先定位Azure提示的物体，确保边界框准确。\n\n");
                
                System.out.println("[QWEN-AZURE-HINTS] ✅ Azure object hints included: " + azureObjectHints);
            } else {
                // WITHOUT Azure hints - free detection
                sb.append("任务1：检测场景中的主要物体并提供边界框\n")
                .append("请检测图像中最重要的2-5个物体，为每个物体提供：\n")
                .append("- id: 使用格式 \"obj1\", \"obj2\", \"obj3\" 等\n")
                .append("- labelZh: 简体中文标签（≤4字）\n")
                .append("- conf: 置信度（0~1）\n")
                .append("- box: 边界框 [x, y, width, height]，坐标范围 0-1000\n")
                .append("注意：优先检测与场景主题相关的物体（如产品、人物、关键道具）。\n\n");
                
                System.out.println("[QWEN-AZURE-HINTS] ⚠️ No Azure hints - using free object detection");
            }
            
            .append("任务2：场景分析\n")
            .append("请在200字内描述以下要素，保持客观一致的描述风格：\n")
            .append("1. 环境类型（室内/室外、场所）\n")
            .append("2. 光线条件（自然光/人工光、明暗）\n")
            .append("3. 色调氛围（主要颜色、情绪）\n")
            .append("4. 主要物体（类型、位置、状态）\n")
            .append("5. 动作活动（如有）\n")
            .append("6. 拍摄角度（俯视/平视/仰视等）\n\n")
            
            .append("返回JSON格式：\n")
            .append("{\n")
            .append("  \"keyElements\": [\n")
            .append("    {\"name\":\"汽车\",\"box\":[100,150,300,200],\"conf\":0.95},\n")
            .append("    {\"name\":\"屏幕\",\"box\":[400,200,200,150],\"conf\":0.90},\n")
            .append("    {\"name\":\"销售场景\",\"box\":null,\"conf\":0.85}\n")
            .append("  ],\n")
            .append("  \"sceneAnalysis\": \"详细的场景分析文字...\"\n")
            .append("}\n\n")
            .append("说明：\n")
            .append("- keyElements: 场景的关键要素（既包括具体物体，也包括抽象概念）\n")
            .append("- name: 关键要素名称（简体中文，≤6字）\n")
            .append("- box: 边界框 [x, y, width, height]，如果是抽象概念则为 null\n")
            .append("- conf: 置信度（0~1）\n")
            .append("- box格式: [x, y, width, height]，左上角为原点，范围0-1000\n\n")
            .append("注意：\n")
            .append("1. 具体物体（如汽车、人物）应提供边界框\n")
            .append("2. 抽象概念（如销售场景、宣传氛围）box设为null\n")
            .append("3. box坐标系统：左上角为原点，x向右，y向下，范围0-1000");

            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            request.put("max_tokens", 2000); // Increase token limit for detailed scene analysis
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", sb.toString());
            content.add(textContent);

            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            Map<String, Object> imageUrl = new HashMap<>();
            String dataUrl = toDataUrlSafe(keyframeUrl);
            imageUrl.put("url", dataUrl);
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);

            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String endpointR = normalizeChatEndpoint(qwenApiBase);
            ResponseEntity<String> response = restTemplate.exchange(
                endpointR,
                HttpMethod.POST,
                entity,
                String.class
            );
            String bodyR = response.getBody();
            System.out.println("[QWEN] regions host=" + safeHost(endpointR) + " model=" + qwenModel +
                " status=" + response.getStatusCodeValue() + " bodyLen=" + (bodyR == null ? 0 : bodyR.length()));
            if (bodyR != null) {
                String head = bodyR.substring(0, Math.min(200, bodyR.length()));
                System.out.println("[QWEN] regions body head=" + head.replaceAll("\n", " "));
                System.out.println("[QWEN] ========== FULL RAW RESPONSE ==========");
                System.out.println(bodyR);
                System.out.println("[QWEN] ========== END RAW RESPONSE ==========");
            }

            if (!response.getStatusCode().is2xxSuccessful()) {
                System.err.println("[QWEN] ❌ Non-successful status code: " + response.getStatusCodeValue());
                return out;
            }
            String contentStr = extractContent(bodyR);
            System.out.println("[QWEN] Extracted content length: " + (contentStr != null ? contentStr.length() : 0));
            System.out.println("[QWEN] Extracted content: " + (contentStr != null ? contentStr.substring(0, Math.min(500, contentStr.length())) : "null"));
            
            if (contentStr == null || contentStr.isBlank()) {
                System.err.println("[QWEN] ❌ Content is null or blank after extraction");
                return out;
            }

            // Parse enhanced JSON response
            JsonNode root;
            String sceneAnalysis = null;
            boolean isValidJson = true;
            
            try {
                root = objectMapper.readTree(contentStr);
                System.out.println("[QWEN] Parsed JSON - isObject: " + root.isObject() + ", isArray: " + root.isArray());
                System.out.println("[QWEN] JSON structure: " + root.toString().substring(0, Math.min(300, root.toString().length())));
            } catch (Exception jsonEx) {
                System.err.println("[QWEN] ⚠️ Failed to parse as JSON, using raw text as scene analysis");
                System.err.println("[QWEN] JSON parse error: " + jsonEx.getMessage());
                isValidJson = false;
                root = null;
                // Store the entire response as scene analysis for later comparison
                sceneAnalysis = contentStr;
                System.out.println("[QWEN] Stored raw response as scene analysis (" + sceneAnalysis.length() + " chars)");
            }
            
            // If not valid JSON, create dummy results with scene analysis for comparison
            if (!isValidJson) {
                System.out.println("[QWEN] Creating fallback results with raw text for comparison");
                // Create a result for each region with the raw text as scene analysis
                for (RegionBox region : regions) {
                    LabelResult result = new LabelResult(region.id, "未识别", 0.5);
                    result.sceneAnalysis = sceneAnalysis;
                    result.rawResponse = contentStr;  // Store raw response
                    out.put(region.id, result);
                    System.out.println("[QWEN] Added fallback region: " + region.id + " with raw scene analysis");
                }
            }
            // Try new unified format: {keyElements: [{name, box, conf}, ...], sceneAnalysis: "..."}
            else if (root.isObject() && root.has("keyElements")) {
                System.out.println("[QWEN] ✅ Found 'keyElements' field in response (unified format)");
                JsonNode keyElementsNode = root.get("keyElements");
                
                // Extract scene analysis
                if (root.has("sceneAnalysis")) {
                    sceneAnalysis = root.get("sceneAnalysis").asText();
                    System.out.println("[QWEN] ✅ Scene analysis extracted (" + sceneAnalysis.length() + " chars)");
                    System.out.println("[QWEN] Scene analysis preview: " + sceneAnalysis.substring(0, Math.min(200, sceneAnalysis.length())) + "...");
                } else {
                    System.err.println("[QWEN] ⚠️ No 'sceneAnalysis' field in response");
                }
                
                // Build unified keyElementsWithBoxes list
                List<com.example.demo.model.Scene.KeyElement> keyElementsWithBoxes = new ArrayList<>();
                
                if (keyElementsNode.isArray()) {
                    System.out.println("[QWEN] Processing " + keyElementsNode.size() + " keyElements (unified format)");
                    
                    int idCounter = 1;
                    for (JsonNode node : keyElementsNode) {
                        String name = node.path("name").asText("");
                        double conf = clamp(node.path("conf").asDouble(0.8));
                        
                        // Extract bounding box if present (can be null for abstract concepts)
                        int[] box = null;
                        JsonNode boxNode = node.get("box");
                        if (boxNode != null && !boxNode.isNull() && boxNode.isArray() && boxNode.size() == 4) {
                            box = new int[4];
                            box[0] = boxNode.get(0).asInt();
                            box[1] = boxNode.get(1).asInt();
                            box[2] = boxNode.get(2).asInt();
                            box[3] = boxNode.get(3).asInt();
                            
                            System.out.println("[QWEN-BOX-DEBUG] ========================================");
                            System.out.println("[QWEN-BOX-DEBUG] KeyElement: " + name);
                            System.out.println("[QWEN-BOX-DEBUG] Raw box from Qwen: [" + box[0] + ", " + box[1] + ", " + box[2] + ", " + box[3] + "]");
                            System.out.println("[QWEN-BOX-DEBUG] Normalized (÷1000): [" + (box[0]/1000.0) + ", " + (box[1]/1000.0) + ", " + (box[2]/1000.0) + ", " + (box[3]/1000.0) + "]");
                            
                            // Check if this looks like [x, y, width, height] or [x1, y1, x2, y2]
                            if (box[2] > 500 || box[3] > 500) {
                                System.out.println("[QWEN-BOX-DEBUG] ⚠️ WARNING: Box values [2] or [3] > 500, might be absolute coordinates instead of dimensions!");
                                System.out.println("[QWEN-BOX-DEBUG] If [x1,y1,x2,y2]: width=" + (box[2]-box[0]) + ", height=" + (box[3]-box[1]));
                            }
                            
                            // Check if box is too large (>40% of frame)
                            if (box[2] > 400 || box[3] > 400) {
                                System.out.println("[QWEN-BOX-DEBUG] ⚠️ WARNING: Large bounding box detected! w=" + box[2] + ", h=" + box[3]);
                            }
                            System.out.println("[QWEN-BOX-DEBUG] ========================================");
                        }
                        
                        if (!name.isEmpty()) {
                            // Build KeyElement (with or without box)
                            float[] normalizedBox = null;
                            if (box != null && box.length == 4) {
                                normalizedBox = new float[4];
                                normalizedBox[0] = box[0] / 1000.0f;  // x
                                normalizedBox[1] = box[1] / 1000.0f;  // y
                                normalizedBox[2] = box[2] / 1000.0f;  // width
                                normalizedBox[3] = box[3] / 1000.0f;  // height
                            }
                            
                            com.example.demo.model.Scene.KeyElement keyElement = 
                                new com.example.demo.model.Scene.KeyElement(name, normalizedBox, (float) conf);
                            keyElementsWithBoxes.add(keyElement);
                            
                            // Also create LabelResult for backward compatibility
                            String id = "obj" + idCounter++;
                            LabelResult result = new LabelResult(id, name, conf);
                            result.sceneAnalysis = sceneAnalysis;
                            result.rawResponse = contentStr;
                            result.keyElementsWithBoxes = keyElementsWithBoxes;
                            result.box = box;
                            out.put(id, result);
                            
                            System.out.println("[QWEN] Added keyElement: " + name + " with box: " + 
                                (normalizedBox != null ? "[" + normalizedBox[0] + "," + normalizedBox[1] + "," + normalizedBox[2] + "," + normalizedBox[3] + "]" : "null (abstract concept)"));
                        }
                    }
                    
                    System.out.println("[QWEN] ✅ Total keyElements: " + keyElementsWithBoxes.size());
                    
                } else {
                    System.err.println("[QWEN] ⚠️ 'keyElements' is not an array");
                }
            }
            // Fallback: old regions format for backward compatibility
            else if (root.isObject() && root.has("regions")) {
                System.out.println("[QWEN] ⚠️ Using legacy 'regions' format");
                JsonNode regionsNode = root.get("regions");
                
                if (root.has("sceneAnalysis")) {
                    sceneAnalysis = root.get("sceneAnalysis").asText();
                }
                
                List<com.example.demo.model.Scene.KeyElement> keyElementsWithBoxes = new ArrayList<>();
                
                if (regionsNode.isArray()) {
                    for (JsonNode node : regionsNode) {
                        String id = node.path("id").asText(null);
                        String label = sanitize(node.path("labelZh").asText(""));
                        double conf = clamp(node.path("conf").asDouble(0.0));
                        
                        int[] box = null;
                        if (node.has("box") && node.get("box").isArray()) {
                            JsonNode boxNode = node.get("box");
                            if (boxNode.size() == 4) {
                                box = new int[4];
                                for (int i = 0; i < 4; i++) {
                                    box[i] = boxNode.get(i).asInt();
                                }
                            }
                        }
                        
                        if (id != null && !label.isEmpty()) {
                            float[] normalizedBox = null;
                            if (box != null && box.length == 4) {
                                normalizedBox = new float[4];
                                for (int i = 0; i < 4; i++) {
                                    normalizedBox[i] = box[i] / 1000.0f;
                                }
                            }
                            
                            com.example.demo.model.Scene.KeyElement keyElement = 
                                new com.example.demo.model.Scene.KeyElement(label, normalizedBox, (float) conf);
                            keyElementsWithBoxes.add(keyElement);
                            
                            LabelResult result = new LabelResult(id, label, conf);
                            result.sceneAnalysis = sceneAnalysis;
                            result.rawResponse = contentStr;
                            result.keyElementsWithBoxes = keyElementsWithBoxes;
                            result.box = box;
                            out.put(id, result);
                        }
                    }
                }
            }
            // Fallback: old format (array) for backward compatibility
            else if (root.isArray()) {
                System.out.println("[QWEN] ⚠️ Using fallback array format (no scene analysis)");
                for (JsonNode node : root) {
                    String id = node.path("id").asText(null);
                    String label = sanitize(node.path("labelZh").asText(""));
                    double conf = clamp(node.path("conf").asDouble(0.0));
                    if (id != null && !label.isEmpty()) {
                        out.put(id, new LabelResult(id, label, conf));
                        System.out.println("[QWEN] Added region (old format): " + id + " -> " + label);
                    }
                }
            } else {
                System.err.println("[QWEN] ❌ Unexpected JSON format - not object with 'regions' or array");
            }
            
            System.out.println("[QWEN] ========================================");
            System.out.println("[QWEN] ✅ Returning " + out.size() + " labeled regions");
            if (!out.isEmpty()) {
                LabelResult first = out.values().iterator().next();
                System.out.println("[QWEN] First result has sceneAnalysis: " + (first.sceneAnalysis != null));
                if (first.sceneAnalysis != null) {
                    System.out.println("[QWEN] Scene analysis length: " + first.sceneAnalysis.length());
                }
            }
            System.out.println("[QWEN] ========================================");
            return out;
        } catch (Exception e) {
            System.err.println("[QWEN] ========================================");
            System.err.println("[QWEN] ❌ Exception in labelRegions: " + e.getClass().getName());
            System.err.println("[QWEN] ❌ Error message: " + e.getMessage());
            System.err.println("[QWEN] ========================================");
            e.printStackTrace();
            return out;
        }
    }


    private String extractContent(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) return null;
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).path("message");
            JsonNode contentNode = message.path("content");

            // A) Array of parts
            if (contentNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : contentNode) {
                    String type = part.path("type").asText("");
                    if ("text".equals(type) || "output_text".equals(type)) {
                        String t = part.path("text").asText("");
                        if (t != null) sb.append(t);
                    }
                }
                String s = stripCodeFences(sb.toString().trim());
                if (!s.isEmpty()) return s;
            }

            // B) Plain string
            if (contentNode.isTextual()) {
                String s = stripCodeFences(contentNode.asText("").trim());
                if (!s.isEmpty()) return s;
            }

            // C) message.output_text
            String mo = stripCodeFences(message.path("output_text").asText("").trim());
            if (!mo.isEmpty()) return mo;
        }
        // D) Top-level fallbacks
        String alt = stripCodeFences(root.path("output_text").asText("").trim());
        if (!alt.isEmpty()) return alt;
        String plain = stripCodeFences(choices.path(0).path("text").asText("").trim());
        return plain.isEmpty() ? null : plain;
    }

    private String stripCodeFences(String s) {
        if (s == null) return null;
        s = s.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1");
        s = s.replaceAll("(?s)```\\s*(.*?)\\s*```", "$1");
        return s.trim();
    }
    private String normalizeChatEndpoint(String base) {
        String def = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        if (base == null || base.isBlank()) return def;

        if (base.startsWith("http")) {
            if (base.endsWith("/chat/completions")) return base;
            if (base.contains("/compatible-mode/") && base.endsWith("/v1")) return base + "/chat/completions";
            try {
                var u = java.net.URI.create(base);
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
     */
    private String toDataUrlSafe(String imageUrl) {
        try {
            java.net.URL url = new java.net.URL(imageUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
                return "data:image/jpeg;base64," + b64;
            }
        } catch (Exception e) {
            // Fallback to original URL if we cannot embed
            return imageUrl;
        }
    }

    private String safeHost(String endpoint) {
        try {
            java.net.URI u = java.net.URI.create(endpoint);
            return u.getScheme() + "://" + u.getHost();
        } catch (Exception e) {
            return "<invalid-endpoint>";
        }
    }

    private String sanitize(String s) {
        if (s == null) return DEFAULT_LABEL;
        s = s.replaceAll("[\\s\\p{Punct}]", "");
        if (s.length() > 4) s = s.substring(0, 4);
        if (!isValidChineseLabel(s)) return DEFAULT_LABEL;
        return s;
    }

    private double clamp(double v) { if (v < 0) return 0; if (v > 1) return 1; return v; }

    @Override
    public String labelZh(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return DEFAULT_LABEL;
        }
        
        // Check cache
        String cacheKey = sha1(imageBytes);
        String cached = labelCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Call Qwen API
        String label = callQwenAPI(imageBytes);
        
        // Validate and retry if needed
        if (!isValidChineseLabel(label)) {
            label = callQwenAPIStricter(imageBytes);
            if (!isValidChineseLabel(label)) {
                label = DEFAULT_LABEL;
            }
        }
        
        // Cache the result
        labelCache.put(cacheKey, label);
        
        return label;
    }
    
    private String callQwenAPI(byte[] imageBytes) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", "识别图中主要物体，只返回一个中文名词，不超过4个字，不要任何其他内容、空格、标点或解释。");
            content.add(textContent);
            
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            Map<String, Object> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes));
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);
            
            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                normalizeChatEndpoint(qwenApiBase),
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return parseQwenResponse(response.getBody());
            }
        } catch (Exception e) {
            System.err.println("Qwen API call failed: " + e.getMessage());
        }
        
        return DEFAULT_LABEL;
    }
    
    private String callQwenAPIStricter(byte[] imageBytes) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", "严格要求：识别主要物体，仅返回2-4个中文字，禁止英文/拼音/空格/标点。示例：苹果、电脑、汽车、手机");
            content.add(textContent);
            
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            Map<String, Object> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes));
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);
            
            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);
            
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
                return parseQwenResponse(response.getBody());
            }
        } catch (Exception e) {
            System.err.println("Qwen API stricter call failed: " + e.getMessage());
        }
        
        return DEFAULT_LABEL;
    }
    
    private String parseQwenResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText("")
                    .trim();
                
                // Clean up common issues
                content = content.replaceAll("[\\s\\p{Punct}]", "");
                
                return content.isEmpty() ? DEFAULT_LABEL : content;
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Qwen response: " + e.getMessage());
        }
        
        return DEFAULT_LABEL;
    }
    
    private boolean isValidChineseLabel(String label) {
        return label != null && CHINESE_PATTERN.matcher(label).matches();
    }
    
    private String sha1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}

    /**
     * Get bounding box for a single object name
     * Used when user edits keyElement and we need to find its bounding box
     */
    @Override
    public float[] getBoundingBoxForObject(String keyframeUrl, String objectName, String locale) {
        System.out.println("[QWEN-SINGLE-BOX] Getting bounding box for object: " + objectName);
        
        try {
            // Build simple prompt to find one object
            StringBuilder sb = new StringBuilder();
            sb.append("请在图像中找到以下物体并提供精确的边界框：\n\n");
            sb.append("物体名称：").append(objectName).append("\n\n");
            sb.append("返回JSON格式：\n");
            sb.append("{\n");
            sb.append("  \"found\": true,\n");
            sb.append("  \"box\": [x, y, width, height]\n");
            sb.append("}\n\n");
            sb.append("说明：\n");
            sb.append("- box格式: [x, y, width, height]，坐标范围 0-1000\n");
            sb.append("- 如果找不到该物体，返回 {\"found\": false}\n");
            
            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            List<Map<String, Object>> content = new ArrayList<>();
            
            // Add image
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image");
            imageContent.put("image", keyframeUrl);
            content.add(imageContent);
            
            // Add text
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", sb.toString());
            content.add(textContent);
            
            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);
            
            // Call Qwen API
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
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                System.err.println("[QWEN-SINGLE-BOX] API returned non-success status: " + response.getStatusCodeValue());
                return null;
            }
            
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                System.err.println("[QWEN-SINGLE-BOX] Empty response body");
                return null;
            }
            
            // Extract content
            String contentStr = extractContentFromResponse(responseBody);
            if (contentStr == null) {
                System.err.println("[QWEN-SINGLE-BOX] Failed to extract content");
                return null;
            }
            
            // Strip markdown if present
            if (contentStr.startsWith("```")) {
                contentStr = contentStr.replaceFirst("^```(?:json)?\\s*", "");
                contentStr = contentStr.replaceFirst("```\\s*$", "");
                contentStr = contentStr.trim();
            }
            
            // Parse JSON
            JsonNode root = objectMapper.readTree(contentStr);
            
            if (!root.has("found") || !root.get("found").asBoolean()) {
                System.out.println("[QWEN-SINGLE-BOX] Object not found: " + objectName);
                return null;
            }
            
            if (!root.has("box") || !root.get("box").isArray()) {
                System.err.println("[QWEN-SINGLE-BOX] No box in response");
                return null;
            }
            
            JsonNode boxNode = root.get("box");
            if (boxNode.size() != 4) {
                System.err.println("[QWEN-SINGLE-BOX] Invalid box size: " + boxNode.size());
                return null;
            }
            
            // Convert from 0-1000 to 0-1 range
            float[] box = new float[4];
            box[0] = (float) boxNode.get(0).asInt() / 1000.0f;  // x
            box[1] = (float) boxNode.get(1).asInt() / 1000.0f;  // y
            box[2] = (float) boxNode.get(2).asInt() / 1000.0f;  // width
            box[3] = (float) boxNode.get(3).asInt() / 1000.0f;  // height
            
            System.out.println("[QWEN-SINGLE-BOX] ✅ Found box for " + objectName + ": [" + 
                box[0] + ", " + box[1] + ", " + box[2] + ", " + box[3] + "]");
            
            return box;
            
        } catch (Exception e) {
            System.err.println("[QWEN-SINGLE-BOX] Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private String extractContentFromResponse(String responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode choices = response.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                return null;
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null) {
                return null;
            }
            JsonNode content = message.get("content");
            return content != null ? content.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
