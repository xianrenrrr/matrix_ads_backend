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

import java.nio.charset.StandardCharsets;
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
              .append("      \"scriptLine\": \"...\",\n")
              .append("      \"presenceOfPerson\": false,\n")
              .append("      \"deviceOrientation\": \"手机（竖屏 9:16）\",\n")
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
            imageUrl.put("url", keyframeUrl);
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
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText("").trim();
        }
        return null;
    }
    private String normalizeChatEndpoint(String base) {
        if (base == null || base.isBlank()) return "/chat/completions";
        if (base.endsWith("/chat/completions")) return base;
        // Dashscope compatible base
        if (base.endsWith("/v1") || base.contains("compatible-mode")) return base + "/chat/completions";
        // If user passed the non-compatible generation endpoint for Dashscope, coerce to compatible chat
        // e.g., https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
        try {
            java.net.URI u = java.net.URI.create(base);
            if (u.getHost() != null && u.getHost().contains("dashscope.aliyuncs.com")) {
                return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            }
        } catch (Exception ignored) {}
        // Otherwise assume fully-qualified endpoint already points to chat
        return base;
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
