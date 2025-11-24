package com.example.demo.dao;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.example.demo.model.ManualTemplate;

public interface TemplateDao {


    /**
     * Saves a new template to the templates table (Firestore collection: templates).
     * @param template the template to save
     * @return the generated template ID
     */
    String createTemplate(ManualTemplate template) throws ExecutionException, InterruptedException;

    ManualTemplate getTemplate(String templateId) throws ExecutionException, InterruptedException;

    List<ManualTemplate> getTemplatesByUserId(String userId) throws ExecutionException, InterruptedException;
    List<ManualTemplate> getAllTemplates() throws ExecutionException, InterruptedException;
    
    // Get templates assigned to a specific group
    List<ManualTemplate> getTemplatesAssignedToGroup(String groupId) throws ExecutionException, InterruptedException;
    
    // Get lightweight template summaries (only essential fields) for a group
    List<java.util.Map<String, Object>> getTemplateSummariesForGroup(String groupId) throws ExecutionException, InterruptedException;

    boolean updateTemplate(String templateId, ManualTemplate manualTemplate) throws ExecutionException, InterruptedException;

    boolean deleteTemplate(String templateId);
    
    // Get templates in a specific folder
    List<ManualTemplate> getTemplatesByFolder(String folderId) throws ExecutionException, InterruptedException;
}