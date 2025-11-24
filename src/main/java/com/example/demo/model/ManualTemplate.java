package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.ArrayList;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManualTemplate {
    private String userId;
    private String templateTitle;
    private String templateDescription;  // NEW: Template description for manual templates
    
    @JsonDeserialize(using = DurationToIntDeserializer.class)
    private int totalVideoLength;
    private String videoPurpose;
    private String tone;
    private List<Scene> scenes;
    private String videoFormat;
    private String lightingRequirements;
    private String backgroundMusic;
    private String id;
    private String videoId;
    private String thumbnailUrl;  // Thumbnail URL for template preview
    private String localeUsed;  // Locale used for template generation (e.g., "zh-CN")
    private String folderId;  // Folder ID for organization (null = root folder)
    private java.util.Date createdAt;  // Template creation timestamp for IAM permission checks
    
    // Additional database fields
    private List<String> submittedVideos;

    public ManualTemplate() {
        this.scenes = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getId() {
        return id;
    }
    
    // Setter
    public void setId(String id) {
        this.id = id;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    // Getters
    public String getTemplateTitle() {
        return templateTitle;
    }
    
    public String getTemplateDescription() {
        return templateDescription;
    }

    public int getTotalVideoLength() {
        return totalVideoLength;
    }

    public String getVideoPurpose() {
        return videoPurpose;
    }

    public String getTone() {
        return tone;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    public String getVideoFormat() {
        return videoFormat;
    }

    public String getLightingRequirements() {
        return lightingRequirements;
    }

    public String getBackgroundMusic() {
        return backgroundMusic;
    }

    // Setters
    public void setTemplateTitle(String templateTitle) {
        this.templateTitle = templateTitle;
    }
    
    public void setTemplateDescription(String templateDescription) {
        this.templateDescription = templateDescription;
    }

    public void setTotalVideoLength(int totalVideoLength) {
        this.totalVideoLength = totalVideoLength;
    }

    public void setVideoPurpose(String videoPurpose) {
        this.videoPurpose = videoPurpose;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public void setScenes(List<Scene> scenes) {
        this.scenes = scenes;
    }

    public void setVideoFormat(String videoFormat) {
        this.videoFormat = videoFormat;
    }

    public void setLightingRequirements(String lightingRequirements) {
        this.lightingRequirements = lightingRequirements;
    }

    public void setBackgroundMusic(String backgroundMusic) {
        this.backgroundMusic = backgroundMusic;
    }
    
    // Getters and setters for additional database fields

    public List<String> getSubmittedVideos() {
        if (submittedVideos == null) {
            submittedVideos = new ArrayList<>();
        }
        return submittedVideos;
    }
    
    public void setSubmittedVideos(List<String> submittedVideos) {
        this.submittedVideos = submittedVideos;
    }
    
    public String getLocaleUsed() {
        return localeUsed;
    }
    
    public void setLocaleUsed(String localeUsed) {
        this.localeUsed = localeUsed;
    }
    
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
    
    public String getFolderId() {
        return folderId;
    }
    
    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }
    
    public java.util.Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(java.util.Date createdAt) {
        this.createdAt = createdAt;
    }

}