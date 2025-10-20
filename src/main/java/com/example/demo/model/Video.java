package com.example.demo.model;

import java.util.List;
import java.util.Map;

public class Video {
    private String id;
    private String userId;
    private String title;
    private String description;
    private String url; // URL or path to the video file
    private String thumbnailUrl;
    private Long durationSeconds; // Video duration in seconds

    private String templateId;
    private List<Map<String, Object>> aiGeneratedScenes;

    public Video() {}

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public List<Map<String, Object>> getAiGeneratedScenes() { return aiGeneratedScenes; }
    public void setAiGeneratedScenes(List<Map<String, Object>> aiGeneratedScenes) { this.aiGeneratedScenes = aiGeneratedScenes; }

    public Video(String id, String userId, String title, String description, String url, String thumbnailUrl, String templateId) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;

        this.templateId = null;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

}
