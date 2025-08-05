package com.example.demo.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * CompiledVideo model for tracking final videos created from approved scene submissions
 * This represents the final product after all scenes are approved and compiled together
 */
public class CompiledVideo {
    private String id;                          // Unique compiled video ID
    private String templateId;                  // Reference to parent template
    private String userId;                      // Content creator who submitted scenes
    private String videoUrl;                    // Firebase Storage URL for final compiled video
    private String thumbnailUrl;                // Auto-generated thumbnail for compiled video
    private String status;                      // "compiling", "completed", "failed", "published"
    private Date compiledAt;                    // When compilation process started
    private Date completedAt;                   // When compilation finished
    private String compiledBy;                  // System or manager who triggered compilation
    
    // Scene References
    private List<String> sceneSubmissionIds;    // Ordered list of approved scene submission IDs
    private int totalScenes;                    // Total number of scenes in template
    private Map<Integer, String> sceneMapping;  // Scene number -> Scene submission ID mapping
    
    // Video Metadata
    private Double totalDuration;               // Total video length in seconds
    private String resolution;                  // Final video resolution
    private String format;                      // Final video format (mp4)
    private Long fileSize;                      // Final file size in bytes
    private String quality;                     // Video quality setting used for compilation
    
    // Compilation Details
    private String compilationJobId;            // FFmpeg job ID for tracking
    private Map<String, Object> compilationSettings; // FFmpeg settings used
    private String compilationLog;              // Compilation process log
    private List<String> compilationErrors;     // Any errors during compilation
    private int retryCount;                     // Number of compilation retries
    
    // Quality Metrics
    private Double overallSimilarityScore;      // Average similarity across all scenes
    private Map<String, Object> finalQualityMetrics; // Final video quality analysis
    private List<String> finalFeedback;         // Manager final approval feedback
    
    // Publishing Information
    private Date publishedAt;                   // When published to external platforms
    private Map<String, String> publishedUrls;  // Platform -> URL mapping (TikTok, etc.)
    private String publishStatus;               // "not_published", "publishing", "published", "failed"
    
    public CompiledVideo() {
        this.status = "compiling";
        this.compiledAt = new Date();
        this.retryCount = 0;
    }
    
    public CompiledVideo(String templateId, String userId, List<String> sceneSubmissionIds) {
        this();
        this.templateId = templateId;
        this.userId = userId;
        this.sceneSubmissionIds = sceneSubmissionIds;
        this.totalScenes = sceneSubmissionIds.size();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Date getCompiledAt() { return compiledAt; }
    public void setCompiledAt(Date compiledAt) { this.compiledAt = compiledAt; }
    
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
    
    public String getCompiledBy() { return compiledBy; }
    public void setCompiledBy(String compiledBy) { this.compiledBy = compiledBy; }
    
    public List<String> getSceneSubmissionIds() { return sceneSubmissionIds; }
    public void setSceneSubmissionIds(List<String> sceneSubmissionIds) { this.sceneSubmissionIds = sceneSubmissionIds; }
    
    public int getTotalScenes() { return totalScenes; }
    public void setTotalScenes(int totalScenes) { this.totalScenes = totalScenes; }
    
    public Map<Integer, String> getSceneMapping() { return sceneMapping; }
    public void setSceneMapping(Map<Integer, String> sceneMapping) { this.sceneMapping = sceneMapping; }
    
    public Double getTotalDuration() { return totalDuration; }
    public void setTotalDuration(Double totalDuration) { this.totalDuration = totalDuration; }
    
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    
    public String getCompilationJobId() { return compilationJobId; }
    public void setCompilationJobId(String compilationJobId) { this.compilationJobId = compilationJobId; }
    
    public Map<String, Object> getCompilationSettings() { return compilationSettings; }
    public void setCompilationSettings(Map<String, Object> compilationSettings) { this.compilationSettings = compilationSettings; }
    
    public String getCompilationLog() { return compilationLog; }
    public void setCompilationLog(String compilationLog) { this.compilationLog = compilationLog; }
    
    public List<String> getCompilationErrors() { return compilationErrors; }
    public void setCompilationErrors(List<String> compilationErrors) { this.compilationErrors = compilationErrors; }
    
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    
    public Double getOverallSimilarityScore() { return overallSimilarityScore; }
    public void setOverallSimilarityScore(Double overallSimilarityScore) { this.overallSimilarityScore = overallSimilarityScore; }
    
    public Map<String, Object> getFinalQualityMetrics() { return finalQualityMetrics; }
    public void setFinalQualityMetrics(Map<String, Object> finalQualityMetrics) { this.finalQualityMetrics = finalQualityMetrics; }
    
    public List<String> getFinalFeedback() { return finalFeedback; }
    public void setFinalFeedback(List<String> finalFeedback) { this.finalFeedback = finalFeedback; }
    
    public Date getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Date publishedAt) { this.publishedAt = publishedAt; }
    
    public Map<String, String> getPublishedUrls() { return publishedUrls; }
    public void setPublishedUrls(Map<String, String> publishedUrls) { this.publishedUrls = publishedUrls; }
    
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    
    // Utility Methods
    public boolean isCompiling() { return "compiling".equals(status); }
    public boolean isCompleted() { return "completed".equals(status); }
    public boolean isFailed() { return "failed".equals(status); }
    public boolean isPublished() { return "published".equals(status); }
    
    public void markCompleted() {
        this.status = "completed";
        this.completedAt = new Date();
    }
    
    public void markFailed(List<String> errors) {
        this.status = "failed";
        this.compilationErrors = errors;
        this.completedAt = new Date();
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    public boolean canRetry() {
        return this.retryCount < 3 && "failed".equals(this.status);
    }
    
    @Override
    public String toString() {
        return String.format("CompiledVideo{id='%s', template='%s', user='%s', status='%s', scenes=%d}", 
                           id, templateId, userId, status, totalScenes);
    }
}