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
     * Generate template metadata in Chinese for video template creation
     * @param request Template metadata request
     * @return AIResponse containing Chinese template metadata
     */
    AIResponse<TemplateMetadata> generateTemplateMetadata(TemplateMetadataRequest request);
    
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
    
    /**
     * Template metadata request
     */
    class TemplateMetadataRequest {
        private String videoTitle;
        private int totalDuration;
        private int sceneCount;
        private List<String> sceneLabels;
        private List<SceneTimingInfo> sceneTimings; // NEW: Individual scene timing
        private String language = "zh-CN";
        
        // Getters and setters
        public String getVideoTitle() { return videoTitle; }
        public void setVideoTitle(String videoTitle) { this.videoTitle = videoTitle; }
        
        public int getTotalDuration() { return totalDuration; }
        public void setTotalDuration(int totalDuration) { this.totalDuration = totalDuration; }
        
        public int getSceneCount() { return sceneCount; }
        public void setSceneCount(int sceneCount) { this.sceneCount = sceneCount; }
        
        public List<String> getSceneLabels() { return sceneLabels; }
        public void setSceneLabels(List<String> sceneLabels) { this.sceneLabels = sceneLabels; }
        
        public List<SceneTimingInfo> getSceneTimings() { return sceneTimings; }
        public void setSceneTimings(List<SceneTimingInfo> sceneTimings) { this.sceneTimings = sceneTimings; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
    
    /**
     * Scene timing information for AI context
     */
    class SceneTimingInfo {
        private int sceneNumber;
        private int startSeconds;
        private int endSeconds;
        private int durationSeconds;
        private List<String> detectedObjects; // Objects detected in this specific scene
        
        // Getters and setters
        public int getSceneNumber() { return sceneNumber; }
        public void setSceneNumber(int sceneNumber) { this.sceneNumber = sceneNumber; }
        
        public int getStartSeconds() { return startSeconds; }
        public void setStartSeconds(int startSeconds) { this.startSeconds = startSeconds; }
        
        public int getEndSeconds() { return endSeconds; }
        public void setEndSeconds(int endSeconds) { this.endSeconds = endSeconds; }
        
        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
        
        public List<String> getDetectedObjects() { return detectedObjects; }
        public void setDetectedObjects(List<String> detectedObjects) { this.detectedObjects = detectedObjects; }
    }
    
    /**
     * Template metadata response
     */
    class TemplateMetadata {
        // 基本信息
        private String videoPurpose;           // 视频目标：产品展示与推广
        private String tone;                   // 语调：专业
        private String videoFormat;            // 视频格式：1080p 16:9
        private String lightingRequirements;   // 灯光要求：良好的自然光或人工照明
        private String backgroundMusic;        // 背景音乐：轻柔的器乐或环境音乐
        
        // 场景元数据
        private List<SceneMetadata> sceneMetadataList;
        
        // Getters and setters
        public String getVideoPurpose() { return videoPurpose; }
        public void setVideoPurpose(String videoPurpose) { this.videoPurpose = videoPurpose; }
        
        public String getTone() { return tone; }
        public void setTone(String tone) { this.tone = tone; }
        
        public String getVideoFormat() { return videoFormat; }
        public void setVideoFormat(String videoFormat) { this.videoFormat = videoFormat; }
        
        public String getLightingRequirements() { return lightingRequirements; }
        public void setLightingRequirements(String lightingRequirements) { this.lightingRequirements = lightingRequirements; }
        
        public String getBackgroundMusic() { return backgroundMusic; }
        public void setBackgroundMusic(String backgroundMusic) { this.backgroundMusic = backgroundMusic; }
        
        public List<SceneMetadata> getSceneMetadataList() { return sceneMetadataList; }
        public void setSceneMetadataList(List<SceneMetadata> sceneMetadataList) { this.sceneMetadataList = sceneMetadataList; }
    }
    
    /**
     * Scene metadata for each scene in template
     */
    class SceneMetadata {
        private String sceneTitle;              // 场景 1: 场景 1
        private int durationSeconds;            // 时长: 30s
        private String scriptLine;              // 脚本
        private boolean presenceOfPerson;       // 是否有人出现: 否
        private String deviceOrientation;       // 设备方向: 手机（竖屏 9:16）
        private String movementInstructions;    // 动作: 静止
        private String backgroundInstructions;  // 背景说明: 使用与示例画面相似的背景
        private String cameraInstructions;      // 摄像指导: 按照示例中显示的构图拍摄
        private String audioNotes;              // 音频备注: 说话清楚，配合场景的语调
        private int activeGridBlock;            // 激活的区块: 5
        
        // Getters and setters
        public String getSceneTitle() { return sceneTitle; }
        public void setSceneTitle(String sceneTitle) { this.sceneTitle = sceneTitle; }
        
        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
        
        public String getScriptLine() { return scriptLine; }
        public void setScriptLine(String scriptLine) { this.scriptLine = scriptLine; }
        
        public boolean isPresenceOfPerson() { return presenceOfPerson; }
        public void setPresenceOfPerson(boolean presenceOfPerson) { this.presenceOfPerson = presenceOfPerson; }
        
        public String getDeviceOrientation() { return deviceOrientation; }
        public void setDeviceOrientation(String deviceOrientation) { this.deviceOrientation = deviceOrientation; }
        
        public String getMovementInstructions() { return movementInstructions; }
        public void setMovementInstructions(String movementInstructions) { this.movementInstructions = movementInstructions; }
        
        public String getBackgroundInstructions() { return backgroundInstructions; }
        public void setBackgroundInstructions(String backgroundInstructions) { this.backgroundInstructions = backgroundInstructions; }
        
        public String getCameraInstructions() { return cameraInstructions; }
        public void setCameraInstructions(String cameraInstructions) { this.cameraInstructions = cameraInstructions; }
        
        public String getAudioNotes() { return audioNotes; }
        public void setAudioNotes(String audioNotes) { this.audioNotes = audioNotes; }
        
        public int getActiveGridBlock() { return activeGridBlock; }
        public void setActiveGridBlock(int activeGridBlock) { this.activeGridBlock = activeGridBlock; }
    }
}