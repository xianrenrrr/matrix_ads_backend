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
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.labelCache = new LabelCache(256);
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
              .append("重要：scenes[*].scriptLine = 解说台词（旁白/解说者对观众说的话），不要写拍摄或设备操作说明。\n")
              .append("风格：口语化、自然、可直接朗读；第二人称或旁白视角；≤40字，使用中文标点；不要表情、英文、参数或代码。\n")
              .append("输入（JSON）：\n")
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
              .append("      \"scriptLine\": \"解说台词（≤40字，口语化）\",\n")
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
    @Override
    public VideoAnalysisResult analyzeSceneVideo(String videoUrl, String locale, String userDescription) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }
        
        try {
            System.out.println("[QWEN-VL] Unified video analysis: " + videoUrl);
            if (userDescription != null && !userDescription.isEmpty()) {
                System.out.println("[QWEN-VL] User context: " + userDescription);
            }
            
            // Build prompt for FREE-FORM scene analysis
            StringBuilder sb = new StringBuilder();
            
            sb.append("请详细分析这个视频，尽可能详细地描述你看到的所有内容：\n\n");
            
            // Add user context if provided
            if (userDescription != null && !userDescription.isEmpty()) {
                sb.append("用户说明：").append(userDescription).append("\n\n");
            }
            
            sb.append("请描述：\n")
              .append("- 场景环境（室内/室外、地点、背景）\n")
              .append("- 主要物体和人物\n")
              .append("- 动作和活动\n")
              .append("- 镜头运动和拍摄角度\n")
              .append("- 光线、色调、氛围\n")
              .append("- 音频（如果有）\n")
              .append("- 任何其他重要细节\n\n")
              .append("请用自然语言详细描述，不需要特定格式。");

            Map<String, Object> request = new HashMap<>();
            request.put("model", qwenModel);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            List<Map<String, Object>> content = new ArrayList<>();
            
            // Text prompt
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", sb.toString());
            content.add(textContent);

            // Video content
            Map<String, Object> videoContent = new HashMap<>();
            videoContent.put("type", "video");
            videoContent.put("video", videoUrl);
            content.add(videoContent);

            message.put("content", content);
            messages.add(message);
            request.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String endpoint = normalizeChatEndpoint(qwenApiBase);
            System.out.println("[QWEN-VL] Calling API endpoint: " + safeHost(endpoint));
            System.out.println("[QWEN-VL] Model: " + qwenModel);
            System.out.println("[QWEN-VL] Video URL: " + videoUrl);
            System.out.println("[QWEN-VL] Sending request to Qwen VL...");
            
            // Set timeout for video analysis (videos can take longer)
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = 
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000);  // 30 seconds connect timeout
            requestFactory.setReadTimeout(240000);     // 240 seconds (4 minutes) read timeout for video analysis
            RestTemplate timeoutRestTemplate = new RestTemplate(requestFactory);
            
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = timeoutRestTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                entity,
                String.class
            );
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("[QWEN-VL] API call completed in " + duration + "ms");
            
            String body = response.getBody();
            System.out.println("[QWEN-VL] Video analysis response status=" + response.getStatusCodeValue() + 
                             " bodyLen=" + (body == null ? 0 : body.length()));
            
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                return null;
            }
            
            String contentStr = extractContent(body);
            if (contentStr == null || contentStr.isBlank()) {
                return null;
            }
            
            // NEW APPROACH: Save raw VL output directly, let reasoning model parse it later
            VideoAnalysisResult result = new VideoAnalysisResult();
            result.rawVLResponse = contentStr;  // Save complete raw response
            result.sceneDescription = contentStr;  // Use raw text as description
            
            // Try to extract JSON if present (optional, for backward compatibility)
            try {
                String jsonStr = extractJsonFromText(contentStr);
                if (jsonStr != null && !jsonStr.isBlank()) {
                    Map<String, Object> resultMap = objectMapper.readValue(
                        jsonStr, 
                        new TypeReference<Map<String, Object>>(){}
                    );
                    
                    // If JSON parsing succeeds, extract structured data
                    String description = (String) resultMap.get("description");
                    if (description != null && !description.isEmpty()) {
                        result.sceneDescription = description;
                    }
                    
                    // Parse objects if present
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> objectsList = (List<Map<String, Object>>) resultMap.get("objects");
                    if (objectsList != null) {
                        result.objects = new ArrayList<>();
                        for (Map<String, Object> obj : objectsList) {
                            VideoAnalysisResult.DetectedObject detObj = new VideoAnalysisResult.DetectedObject();
                            detObj.id = (String) obj.get("id");
                            detObj.labelZh = sanitize((String) obj.get("labelZh"));
                            detObj.labelEn = (String) obj.get("labelEn");
                            detObj.confidence = ((Number) obj.getOrDefault("confidence", 0.0)).doubleValue();
                            detObj.boundingBox = null;
                            detObj.motionDescription = null;
                            result.objects.add(detObj);
                        }
                    }
                }
            } catch (Exception parseEx) {
                // JSON parsing failed - that's OK! We already saved the raw text
                System.out.println("[QWEN-VL] No structured JSON found, using raw text (this is fine)");
            }
            
            // If no objects were extracted, create empty list (not null)
            if (result.objects == null) {
                result.objects = new ArrayList<>();
            }
            
            System.out.println("[QWEN-VL] Video analysis completed: " + 
                             (result.objects != null ? result.objects.size() : 0) + " objects detected");
            return result;
            
        } catch (Exception e) {
            System.err.println("[QWEN-VL] ❌ Video analysis FAILED");
            System.err.println("[QWEN-VL] Error type: " + e.getClass().getName());
            System.err.println("[QWEN-VL] Error message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("[QWEN-VL] Cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            return null;
        }
    }
    
    @Override
    public Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale) {
        Map<String, LabelResult> out = new HashMap<>();
        if (regions == null || regions.isEmpty() || keyframeUrl == null || keyframeUrl.isBlank()) {
            return out;
        }
        try {
            // Build strict prompt (Chinese, <=4 chars, JSON only)
            String regionsJson = objectMapper.writeValueAsString(regions);
            StringBuilder sb = new StringBuilder();
            sb.append("请对图像中的下列区域做中文命名（不超过4个字），逐项返回 JSON 数组。\n")
              .append("要求：\n")
              .append("- 使用简体中文标签，≤4字；不确定填“未知”。\n")
              .append("- 对每个区域返回 0~1 的置信度字段 conf。\n")
              .append("- 仅对提供的坐标框进行命名，不要额外检测其他区域。\n")
              .append("输入：\n")
              .append("- 图像：").append(keyframeUrl).append("\n")
              .append("- 区域（归一化坐标，0~1）：\n")
              .append(regionsJson).append("\n")
              .append("输出格式（仅 JSON 数组）：\n")
              .append("[\n  {\"id\":\"p1\",\"labelZh\":\"汽车\",\"conf\":0.82},\n  {\"id\":\"p2\",\"labelZh\":\"人物\",\"conf\":0.76}\n]");

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
            }

            if (!response.getStatusCode().is2xxSuccessful()) {
                return out;
            }
            String contentStr = extractContent(bodyR);
            if (contentStr == null || contentStr.isBlank()) return out;

            // Parse JSON array
            JsonNode root = objectMapper.readTree(contentStr);
            if (!root.isArray()) return out;
            for (JsonNode node : root) {
                String id = node.path("id").asText(null);
                String label = sanitize(node.path("labelZh").asText(""));
                double conf = clamp(node.path("conf").asDouble(0.0));
                if (id != null && !label.isEmpty()) {
                    out.put(id, new LabelResult(id, label, conf));
                }
            }
            return out;
        } catch (Exception e) {
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
    
    /**
     * Extract JSON object from text that may contain extra content
     * Looks for the first { and matching } to extract valid JSON
     */
    private String extractJsonFromText(String text) {
        if (text == null || text.isBlank()) return null;
        
        // Try to find JSON object boundaries
        int firstBrace = text.indexOf('{');
        if (firstBrace == -1) return null;
        
        // Find matching closing brace
        int braceCount = 0;
        int lastBrace = -1;
        for (int i = firstBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    lastBrace = i;
                    break;
                }
            }
        }
        
        if (lastBrace == -1) return null;
        
        return text.substring(firstBrace, lastBrace + 1);
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
