package com.example.demo.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * SceneSubmission model for tracking individual scene uploads and their approval status
 * This enables scene-by-scene feedback and resubmission instead of entire video rework
 */
public class SceneSubmission {
    private String id;                          // Unique scene submission ID
    private String templateId;                  // Reference to template assignment ID (not original template)
    private String userId;                      // Content creator who submitted
    private int sceneNumber;                    // Scene order in template (1, 2, 3...)
    private String sceneTitle;                  // Title from template scene
    private String videoUrl;                    // OSS Storage URL for scene video
    private String thumbnailUrl;                // Auto-generated thumbnail
    private String status;                      // "pending", "approved" (simplified to just 2 statuses)
    private Date submittedAt;                   // Initial submission timestamp
    private Date lastUpdatedAt;                 // Last status change timestamp
    private Date reviewedAt;                    // When manager reviewed
    private String reviewedBy;                  // Manager ID who reviewed
    
    // Feedback and Quality Metrics
    private Double similarityScore;             // AI similarity score vs template example
    private List<String> feedback;              // Manager feedback comments
    private List<String> aiSuggestions;         // AI-generated improvement suggestions
    private Map<String, Object> qualityMetrics; // Detailed AI analysis (lighting, audio, etc.)
    
    // Scene Metadata
    private Double duration;                    // Scene length in seconds
    private String originalFileName;            // Original uploaded file name
    private Long fileSize;                      // File size in bytes
    private String resolution;                  // Video resolution (e.g., "1080p", "720p")
    private String format;                      // Video format (e.g., "mp4", "mov")
    
    // Resubmission Tracking
    private int resubmissionCount;              // Number of times resubmitted
    private String previousSubmissionId;        // Reference to previous version if resubmitted
    private List<String> resubmissionHistory;   // List of all submission IDs for this scene
    
    // Template Reference Data (cached for performance)
    private String expectedDuration;            // Expected scene duration from template
    private String sceneInstructions;           // Scene instructions from template
    private Map<String, Object> templateSceneData; // Full scene data from template
    
    public SceneSubmission() {
        this.submittedAt = new Date();
        this.lastUpdatedAt = new Date();
        this.status = STATUS_PENDING;
        this.resubmissionCount = 0;
    }
    
    public SceneSubmission(String assignmentId, String userId, int sceneNumber, String sceneTitle) {
        this();
        this.templateId = assignmentId;  // Note: field name is templateId but stores assignment ID
        this.userId = userId;
        this.sceneNumber = sceneNumber;
        this.sceneTitle = sceneTitle;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTemplateId() { return templateId; }  // Returns assignment ID
    public void setTemplateId(String templateId) { this.templateId = templateId; }  // Sets assignment ID
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public int getSceneNumber() { return sceneNumber; }
    public void setSceneNumber(int sceneNumber) { this.sceneNumber = sceneNumber; }
    
    public String getSceneTitle() { return sceneTitle; }
    public void setSceneTitle(String sceneTitle) { this.sceneTitle = sceneTitle; }
    
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status; 
        this.lastUpdatedAt = new Date();
    }
    
    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
    
    public Date getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Date lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
    
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
    
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    
    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double similarityScore) { this.similarityScore = similarityScore; }
    
    public List<String> getFeedback() { return feedback; }
    public void setFeedback(List<String> feedback) { this.feedback = feedback; }
    
    public List<String> getAiSuggestions() { return aiSuggestions; }
    public void setAiSuggestions(List<String> aiSuggestions) { this.aiSuggestions = aiSuggestions; }
    
    public Map<String, Object> getQualityMetrics() { return qualityMetrics; }
    public void setQualityMetrics(Map<String, Object> qualityMetrics) { this.qualityMetrics = qualityMetrics; }
    
    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }
    
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    
    public int getResubmissionCount() { return resubmissionCount; }
    public void setResubmissionCount(int resubmissionCount) { this.resubmissionCount = resubmissionCount; }
    
    public String getPreviousSubmissionId() { return previousSubmissionId; }
    public void setPreviousSubmissionId(String previousSubmissionId) { this.previousSubmissionId = previousSubmissionId; }
    
    public List<String> getResubmissionHistory() { return resubmissionHistory; }
    public void setResubmissionHistory(List<String> resubmissionHistory) { this.resubmissionHistory = resubmissionHistory; }
    
    public String getExpectedDuration() { return expectedDuration; }
    public void setExpectedDuration(String expectedDuration) { this.expectedDuration = expectedDuration; }
    
    public String getSceneInstructions() { return sceneInstructions; }
    public void setSceneInstructions(String sceneInstructions) { this.sceneInstructions = sceneInstructions; }
    
    public Map<String, Object> getTemplateSceneData() { return templateSceneData; }
    public void setTemplateSceneData(Map<String, Object> templateSceneData) { this.templateSceneData = templateSceneData; }
    
    // Status constants
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_RESUBMITTED = "resubmitted";
    
    // Utility Methods
    public boolean isPending() { return STATUS_PENDING.equals(status); }
    public boolean isApproved() { return STATUS_APPROVED.equals(status); }
    public boolean isRejected() { return STATUS_REJECTED.equals(status); }
    public boolean isResubmitted() { return STATUS_RESUBMITTED.equals(status); }
    
    public void incrementResubmissionCount() {
        this.resubmissionCount++;
        this.lastUpdatedAt = new Date();
    }
    
    public void approve(String reviewerId) {
        this.status = STATUS_APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = new Date();
        this.lastUpdatedAt = new Date();
    }
    
    public void reject(String reviewerId, List<String> feedback) {
        // Keep status as "pending" when rejected - allows resubmission
        this.status = STATUS_PENDING;  
        this.reviewedBy = reviewerId;
        this.reviewedAt = new Date();
        this.feedback = feedback;
        this.lastUpdatedAt = new Date();
    }
    
    @Override
    public String toString() {
        return String.format("SceneSubmission{id='%s', assignmentId='%s', user='%s', scene=%d, status='%s'}", 
                           id, templateId, userId, sceneNumber, status);
    }
}