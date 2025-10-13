package com.example.demo.service;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.GroupDao;
import com.example.demo.model.ManualTemplate;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Service wrapper to handle bidirectional template-group relationships
 */
@Service
public class TemplateGroupService {
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private GroupDao groupDao;
    
    @Autowired
    private Firestore db;
    
    /**
     * Creates a template and updates the bidirectional relationship with groups
     */
    public String createTemplateWithGroups(ManualTemplate template, List<String> groupIds) throws ExecutionException, InterruptedException {
        // Create the template
        String templateId = templateDao.createTemplate(template);
        
        // Update bidirectional relationships
        if (groupIds != null && !groupIds.isEmpty()) {
            assignTemplateToGroups(templateId, groupIds);
        }
        
        return templateId;
    }
    
    /**
     * Assigns a template to multiple groups (bidirectional update)
     */
    public void assignTemplateToGroups(String templateId, List<String> groupIds) throws ExecutionException, InterruptedException {
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        
        // Legacy: assignedGroups field deprecated, now using TemplateAssignment
        // This update is kept for backward compatibility but should not be used
        // DocumentReference templateRef = db.collection("templates").document(templateId);
        // templateRef.update("assignedGroups", groupIds).get();
        
        // Update each group with this template ID using GroupDao
        for (String groupId : groupIds) {
            groupDao.addTemplateToGroup(groupId, templateId);
        }
    }
    
    /**
     * Removes a template from all its assigned groups
     */
    public void removeTemplateFromGroups(String templateId) throws ExecutionException, InterruptedException {
        // Get the template document to find assigned groups
        DocumentReference templateRef = db.collection("templates").document(templateId);
        com.google.cloud.firestore.DocumentSnapshot templateDoc = templateRef.get().get();
        
        // Legacy: assignedGroups field deprecated, now using TemplateAssignment
        // Group cleanup is now handled by TemplateAssignmentCleanupScheduler
        // if (templateDoc.exists()) {
        //     @SuppressWarnings("unchecked")
        //     List<String> assignedGroups = (List<String>) templateDoc.get("assignedGroups");
        //     if (assignedGroups != null) {
        //         for (String groupId : assignedGroups) {
        //             groupDao.removeTemplateFromGroup(groupId, templateId);
        //         }
        //     }
        // }
    }
    
    /**
     * Deletes a template and cleans up all relationships
     */
    public boolean deleteTemplateWithCleanup(String templateId) throws ExecutionException, InterruptedException {
        // First remove from all groups
        removeTemplateFromGroups(templateId);
        
        // Then delete the template
        return templateDao.deleteTemplate(templateId);
    }
    
    /**
     * Updates template group assignments
     */
    public void updateTemplateGroups(String templateId, List<String> newGroupIds) throws ExecutionException, InterruptedException {
        // First remove from old groups
        removeTemplateFromGroups(templateId);
        
        // Then assign to new groups
        assignTemplateToGroups(templateId, newGroupIds);
    }
}