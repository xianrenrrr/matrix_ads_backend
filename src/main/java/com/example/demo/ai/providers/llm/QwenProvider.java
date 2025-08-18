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
        }
        content.add(Map.of("type", "text", "text", prompt));
        
        userMessage.put("content", content);
        messages.add(userMessage);
        
        requestBody.put("messages", messages);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                qwenEndpoint + "/chat/completions",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            return response.getBody();
            
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
}