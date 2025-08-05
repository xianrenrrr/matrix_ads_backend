package com.example.demo.model;

import java.util.List;

public class CreateTemplateRequest {
    private String userId;
    private ManualTemplate manualTemplate;
    private String videoId;
    private List<String> selectedGroupIds; // List of group IDs to assign this template to

    public CreateTemplateRequest() {}

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ManualTemplate getManualTemplate() {
        return manualTemplate;
    }

    public void setManualTemplate(ManualTemplate manualTemplate) {
        this.manualTemplate = manualTemplate;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public List<String> getSelectedGroupIds() {
        return selectedGroupIds;
    }

    public void setSelectedGroupIds(List<String> selectedGroupIds) {
        this.selectedGroupIds = selectedGroupIds;
    }
}
