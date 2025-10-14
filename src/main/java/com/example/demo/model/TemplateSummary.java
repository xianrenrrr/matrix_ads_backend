package com.example.demo.model;

/**
 * Lightweight DTO for template list responses
 * Contains only essential fields needed by the frontend
 */
public class TemplateSummary {
    private String id;
    private String templateTitle;
    private String folderId;

    public TemplateSummary() {
    }

    public TemplateSummary(String id, String templateTitle, String folderId) {
        this.id = id;
        this.templateTitle = templateTitle;
        this.folderId = folderId;
    }

    // Static factory method to create from ManualTemplate
    public static TemplateSummary fromManualTemplate(ManualTemplate template) {
        return new TemplateSummary(
            template.getId(),
            template.getTemplateTitle(),
            template.getFolderId()
        );
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTemplateTitle() {
        return templateTitle;
    }

    public void setTemplateTitle(String templateTitle) {
        this.templateTitle = templateTitle;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }
}
