package com.example.demo.ai.comparison.dto;

public class ComparisonRequest {
    private String templateId;
    private String userVideoId;

    public ComparisonRequest() {
    }

    public ComparisonRequest(String templateId, String userVideoId) {
        this.templateId = templateId;
        this.userVideoId = userVideoId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getUserVideoId() {
        return userVideoId;
    }

    public void setUserVideoId(String userVideoId) {
        this.userVideoId = userVideoId;
    }
}