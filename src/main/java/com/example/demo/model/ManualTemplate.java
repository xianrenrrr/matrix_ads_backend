package com.example.demo.model;

import java.util.List;
import java.util.ArrayList;

public class ManualTemplate {
    private String userId;
    private String templateTitle;
    private int totalVideoLength;
    private String targetAudience;
    private String tone;
    private List<Scene> scenes;
    private String videoFormat;
    private String lightingRequirements;
    private String soundRequirements;
    private String id;
    private String videoId;

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

    public int getTotalVideoLength() {
        return totalVideoLength;
    }

    public String getTargetAudience() {
        return targetAudience;
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

    public String getSoundRequirements() {
        return soundRequirements;
    }

    // Setters
    public void setTemplateTitle(String templateTitle) {
        this.templateTitle = templateTitle;
    }

    public void setTotalVideoLength(int totalVideoLength) {
        this.totalVideoLength = totalVideoLength;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
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

    public void setSoundRequirements(String soundRequirements) {
        this.soundRequirements = soundRequirements;
    }


}