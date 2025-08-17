package com.example.demo.model;

import java.util.Date;

/**
 * Simple compiled video model
 */
public class CompiledVideo {
    private String id;
    private String templateId;
    private String userId;
    private String videoUrl;                    // Final compiled video URL
    private String status;                      // "completed", "published" 
    private Date createdAt;
    private String compiledBy;
    
    public CompiledVideo() {
        this.status = "completed";
        this.createdAt = new Date();
    }
    
    public CompiledVideo(String templateId, String userId, String compiledBy) {
        this();
        this.templateId = templateId;
        this.userId = userId;
        this.compiledBy = compiledBy;
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
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    public String getCompiledBy() { return compiledBy; }
    public void setCompiledBy(String compiledBy) { this.compiledBy = compiledBy; }
}