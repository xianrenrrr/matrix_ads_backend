package com.example.demo.model;

import java.time.Instant;

public class Video {
    private String id;
    private String userId;
    private String title;
    private String description;
    private String url; // URL or path to the video file
    private String thumbnailUrl;
    private Instant createdAt;
    private String templateId;

    public Video() {}

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public Video(String id, String userId, String title, String description, String url, String thumbnailUrl, Instant createdAt, String templateId) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.createdAt = createdAt;
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
