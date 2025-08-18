package com.example.demo.ai.providers.llm;

import com.example.demo.ai.core.AIModelProvider;
import com.example.demo.ai.core.AIResponse;
import java.util.List;
import java.util.Map;

/**
 * Interface for Large Language Model providers
 * Handles text generation, translation, and summarization with Chinese-first approach
 */
public interface LLMProvider extends AIModelProvider {
    
    /**
     * Generate Chinese video summary from scene labels and descriptions
     * @param request Video summary request with all context
     * @return AIResponse containing Chinese video summary
     */
    AIResponse<VideoSummary> generateVideoSummary(VideoSummaryRequest request);
    
    /**
     * Generate Chinese labels for detected objects
     * @param englishLabels List of English object labels
     * @return AIResponse containing Chinese translations
     */
    AIResponse<List<String>> generateChineseLabels(List<String> englishLabels);
    
    /**
     * Generate scene improvement suggestions in Chinese
     * @param request Scene analysis request
     * @return AIResponse containing Chinese suggestions
     */
    AIResponse<SceneSuggestions> generateSceneSuggestions(SceneSuggestionsRequest request);
    
    /**
     * Video summary request data
     */
    class VideoSummaryRequest {
        private String videoTitle;
        private List<String> sceneLabels;
        private Map<String, String> sceneDescriptions;
        private String language = "zh-CN"; // Default to Chinese
        
        // Getters and setters
        public String getVideoTitle() { return videoTitle; }
        public void setVideoTitle(String videoTitle) { this.videoTitle = videoTitle; }
        
        public List<String> getSceneLabels() { return sceneLabels; }
        public void setSceneLabels(List<String> sceneLabels) { this.sceneLabels = sceneLabels; }
        
        public Map<String, String> getSceneDescriptions() { return sceneDescriptions; }
        public void setSceneDescriptions(Map<String, String> sceneDescriptions) { this.sceneDescriptions = sceneDescriptions; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
    
    /**
     * Video summary response data
     */
    class VideoSummary {
        private String videoTitleZh;        // ≤12字
        private String videoSummaryZh;      // 2-3句
        private List<String> videoKeywordsZh; // 3-5词
        private String localeUsed = "zh-CN";
        
        // Getters and setters
        public String getVideoTitleZh() { return videoTitleZh; }
        public void setVideoTitleZh(String videoTitleZh) { this.videoTitleZh = videoTitleZh; }
        
        public String getVideoSummaryZh() { return videoSummaryZh; }
        public void setVideoSummaryZh(String videoSummaryZh) { this.videoSummaryZh = videoSummaryZh; }
        
        public List<String> getVideoKeywordsZh() { return videoKeywordsZh; }
        public void setVideoKeywordsZh(List<String> videoKeywordsZh) { this.videoKeywordsZh = videoKeywordsZh; }
        
        public String getLocaleUsed() { return localeUsed; }
        public void setLocaleUsed(String localeUsed) { this.localeUsed = localeUsed; }
    }
    
    /**
     * Scene suggestions request
     */
    class SceneSuggestionsRequest {
        private double similarityScore;
        private String sceneTitle;
        private Map<String, Object> analysisData;
        
        // Getters and setters
        public double getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }
        
        public String getSceneTitle() { return sceneTitle; }
        public void setSceneTitle(String sceneTitle) { this.sceneTitle = sceneTitle; }
        
        public Map<String, Object> getAnalysisData() { return analysisData; }
        public void setAnalysisData(Map<String, Object> analysisData) { this.analysisData = analysisData; }
    }
    
    /**
     * Scene suggestions response
     */
    class SceneSuggestions {
        private List<String> suggestionsZh;    // 2-4条，≤40字/条
        private List<String> nextActionsZh;    // 1-2条，≤20字/条
        
        // Getters and setters
        public List<String> getSuggestionsZh() { return suggestionsZh; }
        public void setSuggestionsZh(List<String> suggestionsZh) { this.suggestionsZh = suggestionsZh; }
        
        public List<String> getNextActionsZh() { return nextActionsZh; }
        public void setNextActionsZh(List<String> nextActionsZh) { this.nextActionsZh = nextActionsZh; }
    }
}