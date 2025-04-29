package com.example.demo.model;

public class CreateTemplateRequest {
    private String userId;
    private ManualTemplate manualTemplate;

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
}
