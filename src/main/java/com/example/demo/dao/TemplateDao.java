package com.example.demo.dao;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.example.demo.model.ManualTemplate;

public interface TemplateDao {

    void connectToFirestore();

    /**
     * Saves a new template to the video_template table (Firestore collection: video_template).
     * @param template the template to save
     * @return the generated template ID
     */
    String createTemplate(ManualTemplate template) throws ExecutionException, InterruptedException;

    ManualTemplate getTemplate(String templateId) throws ExecutionException, InterruptedException;

    List<ManualTemplate> getAllTemplates() throws ExecutionException, InterruptedException;

    boolean updateTemplate(String templateId, ManualTemplate manualTemplate) throws ExecutionException, InterruptedException;

    boolean deleteTemplate(String templateId);
}