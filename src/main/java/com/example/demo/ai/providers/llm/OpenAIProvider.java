package com.example.demo.ai.providers.llm;

import com.example.demo.ai.core.AIModelType;
import com.example.demo.ai.core.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * OpenAI provider as fallback for LLM operations
 * Handles text generation and translation when Qwen is unavailable
 */
@Service
@ConditionalOnProperty(name = "ai.providers.openai.enabled", havingValue = "true")
public class OpenAIProvider implements LLMProvider {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);
    
    @Value("${ai.providers.openai.api-key:}")
    private String apiKey;
    
    @Value("${ai.providers.openai.model:gpt-4}")
    private String model;
    
    @Value("${ai.providers.openai.max-tokens:512}")
    private int maxTokens;
    
    @Value("${ai.providers.openai.temperature:0.7}")
    private double temperature;
    
    private boolean initialized = false;
    
    @Override
    public AIResponse<VideoSummary> generateVideoSummary(VideoSummaryRequest request) {
        if (!isAvailable()) {
            return AIResponse.error("OpenAI provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // TODO: Implement actual OpenAI API call
            // For now, return mock summary with emphasis on Chinese output
            VideoSummary summary = createMockVideoSummary(request);
            
            AIResponse<VideoSummary> response = 
                AIResponse.success(summary, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", model);
            metadata.put("language", "zh-CN");
            metadata.put("fallback_provider", true);
            response.setMetadata(metadata);
            
            log.info("OpenAI generated video summary in {}ms (fallback)", response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("OpenAI video summary generation failed: {}", e.getMessage(), e);
            return AIResponse.error("OpenAI processing error: " + e.getMessage());
        }
    }
    
    @Override
    public AIResponse<List<String>> generateChineseLabels(List<String> englishLabels) {
        if (!isAvailable()) {
            return AIResponse.error("OpenAI provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // TODO: Implement actual OpenAI translation API call
            List<String> chineseLabels = createMockChineseLabels(englishLabels);
            
            AIResponse<List<String>> response = 
                AIResponse.success(chineseLabels, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("OpenAI translated {} labels in {}ms (fallback)", englishLabels.size(), response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("OpenAI label translation failed: {}", e.getMessage(), e);
            return AIResponse.error("OpenAI translation error: " + e.getMessage());
        }
    }
    
    @Override
    public AIResponse<SceneSuggestions> generateSceneSuggestions(SceneSuggestionsRequest request) {
        if (!isAvailable()) {
            return AIResponse.error("OpenAI provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // TODO: Implement actual OpenAI API call for suggestions
            SceneSuggestions suggestions = createMockSceneSuggestions(request);
            
            AIResponse<SceneSuggestions> response = 
                AIResponse.success(suggestions, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("OpenAI generated scene suggestions in {}ms (fallback)", response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("OpenAI scene suggestions failed: {}", e.getMessage(), e);
            return AIResponse.error("OpenAI suggestion error: " + e.getMessage());
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
        return "OpenAI-" + model;
    }
    
    @Override
    public boolean isAvailable() {
        if (!initialized) {
            return checkOpenAIService();
        }
        return true;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("model", model);
        config.put("max_tokens", maxTokens);
        config.put("temperature", temperature);
        config.put("fallback_provider", true);
        return config;
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        if (config.containsKey("model")) {
            this.model = (String) config.get("model");
        }
        if (config.containsKey("max_tokens")) {
            this.maxTokens = (Integer) config.get("max_tokens");
        }
        if (config.containsKey("temperature")) {
            this.temperature = ((Number) config.get("temperature")).doubleValue();
        }
        
        this.initialized = checkOpenAIService();
        log.info("OpenAI provider initialized: available={}, model={}", initialized, model);
    }
    
    @Override
    public int getPriority() {
        return 50; // Medium priority - fallback after Qwen
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
    
    private boolean checkOpenAIService() {
        try {
            // Check if API key is configured
            return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("your_openai_api_key");
        } catch (Exception e) {
            log.warn("OpenAI service health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Create mock video summary - replace with actual OpenAI API call
     */
    private VideoSummary createMockVideoSummary(VideoSummaryRequest request) {
        VideoSummary summary = new VideoSummary();
        summary.setVideoTitleZh("智能产品演示");
        summary.setVideoSummaryZh("这是一个展示创新产品功能和特点的专业视频。通过详细的演示和清晰的解说，帮助观众了解产品价值。");
        summary.setVideoKeywordsZh(Arrays.asList("智能", "产品", "演示", "创新", "功能"));
        summary.setLocaleUsed("zh-CN");
        return summary;
    }
    
    /**
     * Create mock Chinese labels - replace with actual OpenAI API call
     */
    private List<String> createMockChineseLabels(List<String> englishLabels) {
        Map<String, String> translations = new HashMap<>();
        translations.put("person", "人物");
        translations.put("bottle", "瓶子");
        translations.put("car", "汽车");
        translations.put("product", "产品");
        translations.put("phone", "手机");
        translations.put("table", "桌子");
        translations.put("chair", "椅子");
        translations.put("computer", "电脑");
        translations.put("book", "书籍");
        
        return englishLabels.stream()
            .map(label -> translations.getOrDefault(label, label + "_中"))
            .toList();
    }
    
    /**
     * Create mock scene suggestions - replace with actual OpenAI API call
     */
    private SceneSuggestions createMockSceneSuggestions(SceneSuggestionsRequest request) {
        SceneSuggestions suggestions = new SceneSuggestions();
        
        if (request.getSimilarityScore() < 0.7) {
            suggestions.setSuggestionsZh(Arrays.asList(
                "建议调整拍摄角度以获得更好的视觉效果",
                "增强光线设置可以提升画面质量",
                "确保主要对象位于画面中心位置"
            ));
            suggestions.setNextActionsZh(Arrays.asList(
                "重新拍摄当前场景",
                "调整设备设置"
            ));
        } else {
            suggestions.setSuggestionsZh(Arrays.asList(
                "拍摄质量达标",
                "画面构图良好"
            ));
            suggestions.setNextActionsZh(Arrays.asList("继续下一个场景"));
        }
        
        return suggestions;
    }
    
    @Override
    public AIResponse<TemplateMetadata> generateTemplateMetadata(TemplateMetadataRequest request) {
        if (!isAvailable()) {
            return AIResponse.error("OpenAI provider not available");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // TODO: Implement actual OpenAI API call for template metadata
            TemplateMetadata metadata = createMockTemplateMetadata(request);
            
            AIResponse<TemplateMetadata> response = 
                AIResponse.success(metadata, getProviderName(), getModelType());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("OpenAI generated template metadata in {}ms (fallback)", response.getProcessingTimeMs());
            return response;
            
        } catch (Exception e) {
            log.error("OpenAI template metadata failed: {}", e.getMessage(), e);
            return AIResponse.error("OpenAI template metadata error: " + e.getMessage());
        }
    }
    
    /**
     * Create mock template metadata - replace with actual OpenAI API call
     */
    private TemplateMetadata createMockTemplateMetadata(TemplateMetadataRequest request) {
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