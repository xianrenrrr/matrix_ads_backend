package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import java.util.Map;

public class SubmittedVideo {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("uploadedBy")
    private String uploadedBy;
    
    @JsonProperty("assignmentId")
    private String assignmentId;
    
    @JsonProperty("scenes")
    private Map<String, Object> scenes;
    
    @JsonProperty("progress")
    private Map<String, Object> progress;
    
    @JsonProperty("publishStatus")
    private String publishStatus;
    
    @JsonProperty("approvedAt")
    private Date approvedAt;
    
    @JsonProperty("lastUpdated")
    private Date lastUpdated;
    
    @JsonProperty("createdAt")
    private Date createdAt;
    
    @JsonProperty("downloadedBy")
    private String downloadedBy;
    
    @JsonProperty("downloadedAt")
    private Date downloadedAt;
    
    @JsonProperty("compiledVideoUrl")
    private String compiledVideoUrl;
    
    // Constructors
    public SubmittedVideo() {}
    
    public SubmittedVideo(String id) {
        this.id = id;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUploadedBy() {
        return uploadedBy;
    }
    
    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
    
    public String getAssignmentId() {
        return assignmentId;
    }
    
    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }
    
    public Map<String, Object> getScenes() {
        return scenes;
    }
    
    public void setScenes(Map<String, Object> scenes) {
        this.scenes = scenes;
    }
    
    public Map<String, Object> getProgress() {
        return progress;
    }
    
    public void setProgress(Map<String, Object> progress) {
        this.progress = progress;
    }
    
    public String getPublishStatus() {
        return publishStatus;
    }
    
    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }
    
    public Date getApprovedAt() {
        return approvedAt;
    }
    
    public void setApprovedAt(Date approvedAt) {
        this.approvedAt = approvedAt;
    }
    
    public Date getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getDownloadedBy() {
        return downloadedBy;
    }
    
    public void setDownloadedBy(String downloadedBy) {
        this.downloadedBy = downloadedBy;
    }
    
    public Date getDownloadedAt() {
        return downloadedAt;
    }
    
    public void setDownloadedAt(Date downloadedAt) {
        this.downloadedAt = downloadedAt;
    }
    
    public String getCompiledVideoUrl() {
        return compiledVideoUrl;
    }
    
    public void setCompiledVideoUrl(String compiledVideoUrl) {
        this.compiledVideoUrl = compiledVideoUrl;
    }
}
