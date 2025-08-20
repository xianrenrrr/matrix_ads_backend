package com.example.demo.ai.providers.llm;

import com.example.demo.ai.core.AIModelType;
import com.example.demo.ai.core.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.*;

/**
 * Qwen2.5-VL provider for Chinese-first text generation
 * Specializes in Chinese video descriptions, labels, and suggestions
 */
@Service
@ConditionalOnProperty(name = "ai.providers.qwen.enabled", havingValue = "true")
public class QwenProvider implements LLMProvider {
    
    private static final Logger log = LoggerFactory.getLogger(QwenProvider.class);
    
    @Value("${ai.providers.qwen.model:qwen-vl-plus}")
    private String modelName;
    
    @Value("${ai.providers.qwen.endpoint:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String qwenEndpoint;
    
    @Value("${ai.providers.qwen.api-key:}")
    private String apiKey;
    
    @Value("${ai.providers.qwen.max-tokens:512}")
    private int maxTokens;
    
    @Value("${ai.providers.qwen.temperature:0.7}")
    private double temperature;
    
    private boolean initialized = false;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Override
    public AIResponse<VideoSummary> generateVideoSummary(VideoSummaryRequest request) {
        if (!isAvailable()) {
            return AIResponse.error("Qwen provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Real Qwen2.5-VL API call for Chinese video summary
            VideoSummary summary = callQwenForVideoSummary(request);
            
            AIResponse<VideoSummary> response = 
                AIResponse.success(summary, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", modelName);
            metadata.put("language", "zh-CN");
            metadata.put("scenes_analyzed", request.getSceneLabels().size());
            response.setMetadata(metadata);
            
            log.info("Qwen generated video summary in {}ms", response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("Qwen video summary generation failed: {}", e.getMessage(), e);
            return AIResponse.error("Qwen processing error: " + e.getMessage());
        }
    }
    
    @Override
    public AIResponse<List<String>> generateChineseLabels(List<String> englishLabels) {
        if (!isAvailable()) {
            return AIResponse.error("Qwen provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Real Qwen VL API call for Chinese label translation
            List<String> chineseLabels = callQwenForChineseLabels(englishLabels);
            
            AIResponse<List<String>> response = 
                AIResponse.success(chineseLabels, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("Qwen translated {} labels in {}ms", englishLabels.size(), response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("Qwen label translation failed: {}", e.getMessage(), e);
            return AIResponse.error("Qwen translation error: " + e.getMessage());
        }
    }
    
    @Override
    public AIResponse<SceneSuggestions> generateSceneSuggestions(SceneSuggestionsRequest request) {
        if (!isAvailable()) {
            return AIResponse.error("Qwen provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Real Qwen VL API call for Chinese scene suggestions
            SceneSuggestions suggestions = callQwenForSceneSuggestions(request);
            
            AIResponse<SceneSuggestions> response = 
                AIResponse.success(suggestions, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("Qwen generated scene suggestions in {}ms", response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("Qwen scene suggestions failed: {}", e.getMessage(), e);
            return AIResponse.error("Qwen suggestion error: " + e.getMessage());
        }
    }
    
    // =========================
    // AIModelProvider Interface
    // =========================
    
    @Override
    public AIModelType getModelType() {
        return AIModelType.LLM;
    }
    
    @Override
    public String getProviderName() {
        return "Qwen-" + modelName;
    }
    
    @Override
    public boolean isAvailable() {
        if (!initialized) {
            return checkQwenService();
        }
        return true;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("model", modelName);
        config.put("endpoint", qwenEndpoint);
        config.put("max_tokens", maxTokens);
        config.put("temperature", temperature);
        config.put("language", "zh-CN");
        return config;
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        if (config.containsKey("model")) {
            this.modelName = (String) config.get("model");
        }
        if (config.containsKey("max_tokens")) {
            this.maxTokens = (Integer) config.get("max_tokens");
        }
        if (config.containsKey("temperature")) {
            this.temperature = ((Number) config.get("temperature")).doubleValue();
        }
        
        this.initialized = checkQwenService();
        log.info("Qwen provider initialized: available={}, model={}", initialized, modelName);
    }
    
    @Override
    public int getPriority() {
        return 100; // Highest priority for Chinese text generation
    }
    
    @Override
    public boolean supportsOperation(String operation) {
        switch (operation) {
            case "generateVideoSummary":
            case "generateChineseLabels":
            case "generateSceneSuggestions":
            case "generateTemplateMetadata":
                return true;
            default:
                return false;
        }
    }
    
    // =========================
    // Private Methods
    // =========================
    
    private boolean checkQwenService() {
        try {
            // TODO: Implement actual health check to Qwen endpoint
            return qwenEndpoint != null && !qwenEndpoint.isEmpty();
        } catch (Exception e) {
            log.warn("Qwen service health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Real Qwen2.5-VL API call for Chinese video summary
     */
    private VideoSummary callQwenForVideoSummary(VideoSummaryRequest request) {
        try {
            // Build Chinese prompt for video summary
            String prompt = buildVideoSummaryPrompt(request);
            
            // Call Qwen API
            Map<String, Object> response = callQwenAPI(prompt, null);
            
            // Parse response and extract Chinese summary
            return parseVideoSummaryResponse(response, request);
            
        } catch (Exception e) {
            log.warn("Qwen API call failed, using fallback: {}", e.getMessage());
            return createMockVideoSummary(request);
        }
    }
    
    /**
     * Call Qwen API with OpenAI-compatible format
     */
    private Map<String, Object> callQwenAPI(String prompt, String imageUrl) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("DASHSCOPE_API_KEY not configured");
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        
        // Build messages in OpenAI format
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // System message for Chinese output
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", Arrays.asList(
            Map.of("type", "text", "text", "你是一个专业的中文视频分析助手。请直接使用中文回复，不要翻译。")
        ));
        messages.add(systemMessage);
        
        // User message with text (and image if provided)
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        
        List<Map<String, Object>> content = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));
            log.info("Adding image to Qwen request: {}", imageUrl);
        } else {
            log.info("No image provided, text-only request to Qwen");
        }
        content.add(Map.of("type", "text", "text", prompt));
        
        userMessage.put("content", content);
        messages.add(userMessage);
        
        log.info("Qwen request content structure: {}", content);
        
        requestBody.put("messages", messages);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                qwenEndpoint + "/chat/completions",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            log.debug("Qwen API response: {} (status: {})", responseBody != null ? "success" : "empty", response.getStatusCode());
            
            return responseBody;
            
        } catch (Exception e) {
            log.error("Qwen API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Qwen API error: " + e.getMessage());
        }
    }
    
    /**
     * Build Chinese prompt for video summary
     */
    private String buildVideoSummaryPrompt(VideoSummaryRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下视频内容生成中文总结。要求：\n\n");
        prompt.append("视频标题：").append(request.getVideoTitle()).append("\n");
        
        // Add user's custom description if provided
        if (request.getUserDescription() != null && !request.getUserDescription().trim().isEmpty()) {
            prompt.append("用户需求描述：").append(request.getUserDescription()).append("\n");
            log.info("📝 Adding user description to video summary prompt: {}", request.getUserDescription());
        }
        
        prompt.append("场景标签：").append(String.join("、", request.getSceneLabels())).append("\n");
        
        if (request.getSceneDescriptions() != null && !request.getSceneDescriptions().isEmpty()) {
            prompt.append("场景描述：").append(String.join("；", request.getSceneDescriptions().values())).append("\n");
        }
        
        prompt.append("\n请按以下格式输出（严格按照格式，不要添加其他内容）：\n");
        prompt.append("视频标题：[≤12字的中文标题]\n");
        prompt.append("视频总结：[2-3句话的中文描述]\n");
        prompt.append("关键词：[3-5个中文关键词，用逗号分隔]\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse Qwen API response to VideoSummary
     */
    private VideoSummary parseVideoSummaryResponse(Map<String, Object> response, VideoSummaryRequest request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");
                
                return parseChineseVideoSummary(content);
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse Qwen response: {}", e.getMessage());
        }
        
        // Fallback
        return createMockVideoSummary(request);
    }
    
    /**
     * Parse Chinese text response into structured VideoSummary
     */
    private VideoSummary parseChineseVideoSummary(String content) {
        VideoSummary summary = new VideoSummary();
        summary.setLocaleUsed("zh-CN");
        
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("视频标题：")) {
                    summary.setVideoTitleZh(line.substring(5).trim());
                } else if (line.startsWith("视频总结：")) {
                    summary.setVideoSummaryZh(line.substring(5).trim());
                } else if (line.startsWith("关键词：")) {
                    String keywords = line.substring(4).trim();
                    summary.setVideoKeywordsZh(Arrays.asList(keywords.split("[，,]")));
                }
            }
            
            // Validate required fields
            if (summary.getVideoTitleZh() == null || summary.getVideoTitleZh().isEmpty()) {
                summary.setVideoTitleZh("AI生成视频");
            }
            if (summary.getVideoSummaryZh() == null || summary.getVideoSummaryZh().isEmpty()) {
                summary.setVideoSummaryZh("通过AI技术生成的专业视频内容。");
            }
            if (summary.getVideoKeywordsZh() == null || summary.getVideoKeywordsZh().isEmpty()) {
                summary.setVideoKeywordsZh(Arrays.asList("视频", "内容", "AI"));
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse Chinese summary text: {}", e.getMessage());
            // Return basic fallback
            summary.setVideoTitleZh("AI视频总结");
            summary.setVideoSummaryZh("AI分析生成的视频内容总结。");
            summary.setVideoKeywordsZh(Arrays.asList("AI", "视频", "总结"));
        }
        
        return summary;
    }

    /**
     * Real Qwen VL API call for Chinese label translation
     */
    private List<String> callQwenForChineseLabels(List<String> englishLabels) {
        try {
            // Build Chinese translation prompt
            String prompt = buildChineseLabelsPrompt(englishLabels);
            
            // Call Qwen VL API
            Map<String, Object> response = callQwenAPI(prompt, null);
            
            // Parse response and extract Chinese labels
            return parseChineseLabelsResponse(response, englishLabels);
            
        } catch (Exception e) {
            log.warn("Qwen Chinese labels API call failed, using fallback: {}", e.getMessage());
            return createMockChineseLabels(englishLabels);
        }
    }
    
    /**
     * Real Qwen VL API call for Chinese scene suggestions
     */
    private SceneSuggestions callQwenForSceneSuggestions(SceneSuggestionsRequest request) {
        try {
            // Build Chinese suggestions prompt
            String prompt = buildSceneSuggestionsPrompt(request);
            
            // Call Qwen VL API
            Map<String, Object> response = callQwenAPI(prompt, null);
            
            // Parse response and extract suggestions
            return parseSceneSuggestionsResponse(response, request);
            
        } catch (Exception e) {
            log.warn("Qwen scene suggestions API call failed, using fallback: {}", e.getMessage());
            return createMockSceneSuggestions(request);
        }
    }
    
    /**
     * Build Chinese prompt for label translation
     */
    private String buildChineseLabelsPrompt(List<String> englishLabels) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请将以下英文物体标签翻译成中文。要求：\n\n");
        prompt.append("英文标签：").append(String.join("、", englishLabels)).append("\n\n");
        prompt.append("翻译要求：\n");
        prompt.append("- 每个中文标签不超过4个字\n");
        prompt.append("- 使用最常见的中文词汇\n");
        prompt.append("- 按原顺序排列\n\n");
        prompt.append("请按以下格式输出（严格按照格式，不要添加其他内容）：\n");
        prompt.append("中文标签：[用逗号分隔的中文标签]\n");
        
        return prompt.toString();
    }
    
    /**
     * Build Chinese prompt for scene suggestions
     */
    private String buildSceneSuggestionsPrompt(SceneSuggestionsRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下场景分析结果，生成中文改进建议。\n\n");
        prompt.append("场景标题：").append(request.getSceneTitle()).append("\n");
        prompt.append("相似度得分：").append(String.format("%.2f", request.getSimilarityScore())).append("\n");
        
        if (request.getAnalysisData() != null && !request.getAnalysisData().isEmpty()) {
            prompt.append("分析数据：").append(request.getAnalysisData().toString()).append("\n");
        }
        
        prompt.append("\n请按以下格式输出（严格按照格式，不要添加其他内容）：\n");
        
        if (request.getSimilarityScore() < 0.7) {
            prompt.append("改进建议：[2-4条建议，每条不超过40字，用分号分隔]\n");
            prompt.append("下步行动：[1-2条行动，每条不超过20字，用分号分隔]\n");
        } else {
            prompt.append("改进建议：[1-2条简短建议，每条不超过40字，用分号分隔]\n");
            prompt.append("下步行动：[1条行动，不超过20字]\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * Parse Qwen API response to Chinese labels
     */
    private List<String> parseChineseLabelsResponse(Map<String, Object> response, List<String> fallbackLabels) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");
                
                return parseChineseLabelsText(content, fallbackLabels.size());
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse Qwen Chinese labels response: {}", e.getMessage());
        }
        
        // Fallback
        return createMockChineseLabels(fallbackLabels);
    }
    
    /**
     * Parse Qwen API response to SceneSuggestions
     */
    private SceneSuggestions parseSceneSuggestionsResponse(Map<String, Object> response, SceneSuggestionsRequest request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");
                
                return parseSceneSuggestionsText(content);
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse Qwen scene suggestions response: {}", e.getMessage());
        }
        
        // Fallback
        return createMockSceneSuggestions(request);
    }
    
    /**
     * Parse Chinese labels text response
     */
    private List<String> parseChineseLabelsText(String content, int expectedCount) {
        List<String> labels = new ArrayList<>();
        
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("中文标签：")) {
                    String labelsText = line.substring(5).trim();
                    String[] labelArray = labelsText.split("[，,；;]");
                    for (String label : labelArray) {
                        String cleanLabel = label.trim();
                        if (!cleanLabel.isEmpty() && cleanLabel.length() <= 4) {
                            labels.add(cleanLabel);
                        }
                    }
                    break;
                }
            }
            
            // Ensure we have the expected number of labels
            while (labels.size() < expectedCount) {
                labels.add("物体" + (labels.size() + 1));
            }
            
            // Trim to expected count
            if (labels.size() > expectedCount) {
                labels = labels.subList(0, expectedCount);
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse Chinese labels text: {}", e.getMessage());
            // Return basic fallback
            for (int i = 0; i < expectedCount; i++) {
                labels.add("物体" + (i + 1));
            }
        }
        
        return labels;
    }
    
    /**
     * Parse scene suggestions text response
     */
    private SceneSuggestions parseSceneSuggestionsText(String content) {
        SceneSuggestions suggestions = new SceneSuggestions();
        
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("改进建议：")) {
                    String suggestionsText = line.substring(5).trim();
                    suggestions.setSuggestionsZh(Arrays.asList(suggestionsText.split("[；;]")));
                } else if (line.startsWith("下步行动：")) {
                    String actionsText = line.substring(5).trim();
                    suggestions.setNextActionsZh(Arrays.asList(actionsText.split("[；;]")));
                }
            }
            
            // Ensure we have some suggestions
            if (suggestions.getSuggestionsZh() == null || suggestions.getSuggestionsZh().isEmpty()) {
                suggestions.setSuggestionsZh(Arrays.asList("画面质量良好"));
            }
            if (suggestions.getNextActionsZh() == null || suggestions.getNextActionsZh().isEmpty()) {
                suggestions.setNextActionsZh(Arrays.asList("继续录制"));
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse scene suggestions text: {}", e.getMessage());
            // Return basic fallback
            suggestions.setSuggestionsZh(Arrays.asList("请检查视频质量"));
            suggestions.setNextActionsZh(Arrays.asList("重新录制"));
        }
        
        return suggestions;
    }

    /**
     * Create mock video summary - fallback method
     */
    private VideoSummary createMockVideoSummary(VideoSummaryRequest request) {
        VideoSummary summary = new VideoSummary();
        summary.setVideoTitleZh("产品展示视频");
        summary.setVideoSummaryZh("展示产品特点和使用方法的专业视频。通过清晰的镜头和详细的说明，让观众全面了解产品优势。");
        summary.setVideoKeywordsZh(Arrays.asList("产品", "展示", "专业", "清晰", "优势"));
        summary.setLocaleUsed("zh-CN");
        return summary;
    }
    
    /**
     * Create mock Chinese labels - replace with actual Qwen API call
     */
    private List<String> createMockChineseLabels(List<String> englishLabels) {
        Map<String, String> translations = new HashMap<>();
        translations.put("person", "人");
        translations.put("bottle", "瓶子");
        translations.put("car", "汽车");
        translations.put("product", "产品");
        translations.put("phone", "手机");
        translations.put("table", "桌子");
        translations.put("chair", "椅子");
        
        return englishLabels.stream()
            .map(label -> translations.getOrDefault(label, label))
            .toList();
    }
    
    /**
     * Create mock scene suggestions - replace with actual Qwen API call
     */
    private SceneSuggestions createMockSceneSuggestions(SceneSuggestionsRequest request) {
        SceneSuggestions suggestions = new SceneSuggestions();
        
        if (request.getSimilarityScore() < 0.7) {
            suggestions.setSuggestionsZh(Arrays.asList(
                "画面构图需要调整，建议将主体居中",
                "光线不够明亮，建议增加补光",
                "背景过于杂乱，建议选择简洁背景"
            ));
            suggestions.setNextActionsZh(Arrays.asList(
                "重新录制该场景",
                "调整摄像头角度"
            ));
        } else {
            suggestions.setSuggestionsZh(Arrays.asList(
                "画面质量良好",
                "构图合理"
            ));
            suggestions.setNextActionsZh(Arrays.asList("继续下一场景"));
        }
        
        return suggestions;
    }
    
    @Override
    public AIResponse<TemplateMetadata> generateTemplateMetadata(TemplateMetadataRequest request) {
        if (!isAvailable()) {
            return AIResponse.error("Qwen provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Real Qwen VL API call for Chinese template metadata
            TemplateMetadata metadata = callQwenForTemplateMetadata(request);
            
            AIResponse<TemplateMetadata> response = 
                AIResponse.success(metadata, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("Qwen generated template metadata in {}ms", response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("Qwen template metadata failed: {}", e.getMessage(), e);
            return AIResponse.error("Qwen template metadata error: " + e.getMessage());
        }
    }
    
    /**
     * Real Qwen VL API call for Chinese template metadata generation
     */
    private TemplateMetadata callQwenForTemplateMetadata(TemplateMetadataRequest request) throws Exception {
        String prompt = buildTemplateMetadataPrompt(request);
        Map<String, Object> response = callQwenAPI(prompt, null); // No image needed for metadata
        
        // Extract response text from Qwen API format
        String responseText = null;
        if (response != null) {
            if (response.containsKey("output")) {
                responseText = (String) response.get("output");
            } else if (response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    responseText = (String) message.get("content");
                }
            } else {
                log.warn("Unexpected Qwen response format. Available keys: {}", response.keySet());
            }
        } else {
            log.error("Qwen API returned null response!");
        }
        
        return parseTemplateMetadataResponse(responseText, request);
    }
    
    /**
     * Build prompt for template metadata generation
     */
    private String buildTemplateMetadataPrompt(TemplateMetadataRequest request) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请为以下视频生成模板元数据，使用中文输出：\n")
              .append("视频标题：").append(request.getVideoTitle()).append("\n");
        
        // Add user's custom description if provided
        if (request.getUserDescription() != null && !request.getUserDescription().trim().isEmpty()) {
            prompt.append("用户需求描述：").append(request.getUserDescription()).append("\n");
            log.info("📝 Adding user description to template prompt: {}", request.getUserDescription());
        }
        
        prompt.append("场景数量：").append(request.getSceneCount()).append("\n")
              .append("总时长：").append(request.getTotalDuration()).append("秒\n")
              .append("场景标签：").append(request.getSceneLabels() != null ? String.join(", ", request.getSceneLabels()) : "无").append("\n\n");
        
        // NEW: Add detailed scene timing information
        if (request.getSceneTimings() != null && !request.getSceneTimings().isEmpty()) {
            prompt.append("详细场景信息：\n");
            for (SceneTimingInfo timing : request.getSceneTimings()) {
                prompt.append("场景").append(timing.getSceneNumber())
                      .append("：").append(timing.getStartSeconds()).append("-").append(timing.getEndSeconds()).append("秒")
                      .append("（时长：").append(timing.getDurationSeconds()).append("秒）");
                
                if (timing.getDetectedObjects() != null && !timing.getDetectedObjects().isEmpty()) {
                    prompt.append("，检测到对象：").append(String.join(", ", timing.getDetectedObjects()));
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("请按以下格式输出：\n")
              .append("视频目标：[简短描述]\n")
              .append("语调：[专业/轻松/正式等]\n")
              .append("视频格式：1080p 16:9\n")
              .append("灯光要求：[照明建议]\n")
              .append("背景音乐：[音乐建议]\n\n")
              .append("对于每个场景，请生成：\n")
              .append("场景[序号]：[场景标题]\n")
              .append("脚本：[拍摄指导文字]\n")
              .append("是否有人出现：是/否\n")
              .append("设备方向：手机（竖屏 9:16）\n")
              .append("动作：[动作指导]\n")
              .append("背景说明：[背景要求]\n")
              .append("摄像指导：[构图建议]\n")
              .append("音频备注：[声音要求]\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse Qwen API response for template metadata
     */
    private TemplateMetadata parseTemplateMetadataResponse(String responseText, TemplateMetadataRequest request) {
        TemplateMetadata metadata = new TemplateMetadata();
        
        try {
            String[] lines = responseText.split("\n");
            List<SceneMetadata> sceneMetadataList = new ArrayList<>();
            SceneMetadata currentScene = null;
            
            for (String line : lines) {
                line = line.trim();
                
                // Parse basic metadata
                if (line.startsWith("视频目标：")) {
                    metadata.setVideoPurpose(line.substring(4).trim());
                } else if (line.startsWith("语调：")) {
                    metadata.setTone(line.substring(3).trim());
                } else if (line.startsWith("视频格式：")) {
                    metadata.setVideoFormat(line.substring(4).trim());
                } else if (line.startsWith("灯光要求：")) {
                    metadata.setLightingRequirements(line.substring(4).trim());
                } else if (line.startsWith("背景音乐：")) {
                    metadata.setBackgroundMusic(line.substring(4).trim());
                }
                // Parse scene metadata
                else if (line.matches("场景\\d+：.*")) {
                    if (currentScene != null) {
                        sceneMetadataList.add(currentScene);
                    }
                    currentScene = new SceneMetadata();
                    currentScene.setSceneTitle(line.substring(line.indexOf("：") + 1).trim());
                    currentScene.setDurationSeconds(request.getTotalDuration() / request.getSceneCount());
                    currentScene.setActiveGridBlock(5); // Default center
                } else if (currentScene != null) {
                    if (line.startsWith("脚本：")) {
                        currentScene.setScriptLine(line.substring(3).trim());
                    } else if (line.startsWith("是否有人出现：")) {
                        currentScene.setPresenceOfPerson("是".equals(line.substring(6).trim()));
                    } else if (line.startsWith("设备方向：")) {
                        currentScene.setDeviceOrientation(line.substring(4).trim());
                    } else if (line.startsWith("动作：")) {
                        currentScene.setMovementInstructions(line.substring(3).trim());
                    } else if (line.startsWith("背景说明：")) {
                        currentScene.setBackgroundInstructions(line.substring(4).trim());
                    } else if (line.startsWith("摄像指导：")) {
                        currentScene.setCameraInstructions(line.substring(4).trim());
                    } else if (line.startsWith("音频备注：")) {
                        currentScene.setAudioNotes(line.substring(4).trim());
                    }
                }
            }
            
            // Add the last scene
            if (currentScene != null) {
                sceneMetadataList.add(currentScene);
            }
            
            metadata.setSceneMetadataList(sceneMetadataList);
            
            // Set defaults if not provided
            if (metadata.getVideoPurpose() == null) metadata.setVideoPurpose("产品展示与推广");
            if (metadata.getTone() == null) metadata.setTone("专业");
            if (metadata.getVideoFormat() == null) metadata.setVideoFormat("1080p 16:9");
            if (metadata.getLightingRequirements() == null) metadata.setLightingRequirements("良好的自然光或人工照明");
            if (metadata.getBackgroundMusic() == null) metadata.setBackgroundMusic("轻柔的器乐或环境音乐");
            
        } catch (Exception e) {
            log.warn("Failed to parse template metadata response: {}", e.getMessage());
            // Return fallback
            return createFallbackTemplateMetadata(request);
        }
        
        return metadata;
    }
    
    /**
     * Create fallback template metadata when parsing fails
     */
    private TemplateMetadata createFallbackTemplateMetadata(TemplateMetadataRequest request) {
        TemplateMetadata metadata = new TemplateMetadata();
        
        // 基本信息
        metadata.setVideoPurpose("产品展示与推广");
        metadata.setTone("专业");
        metadata.setVideoFormat("1080p 16:9");
        metadata.setLightingRequirements("良好的自然光或人工照明");
        metadata.setBackgroundMusic("轻柔的器乐或环境音乐");
        
        // 创建场景元数据
        List<SceneMetadata> sceneMetadataList = new ArrayList<>();
        for (int i = 1; i <= request.getSceneCount(); i++) {
            SceneMetadata sceneMetadata = new SceneMetadata();
            sceneMetadata.setSceneTitle("场景 " + i);
            sceneMetadata.setDurationSeconds(request.getTotalDuration() / request.getSceneCount());
            sceneMetadata.setScriptLine("请按照示例画面录制场景 " + i + " 的内容");
            sceneMetadata.setPresenceOfPerson(false);
            sceneMetadata.setDeviceOrientation("手机（竖屏 9:16）");
            sceneMetadata.setMovementInstructions("静止");
            sceneMetadata.setBackgroundInstructions("使用与示例画面相似的背景");
            sceneMetadata.setCameraInstructions("按照示例中显示的构图拍摄");
            sceneMetadata.setAudioNotes("说话清楚，配合场景的语调");
            sceneMetadata.setActiveGridBlock(5); // 中心区域
            
            sceneMetadataList.add(sceneMetadata);
        }
        
        metadata.setSceneMetadataList(sceneMetadataList);
        return metadata;
    }
}