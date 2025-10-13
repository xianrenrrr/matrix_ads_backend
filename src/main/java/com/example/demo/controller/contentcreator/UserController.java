package com.example.demo.controller.contentcreator;

import com.example.demo.model.ManualTemplate;
import com.example.demo.dao.GroupDao;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/content-creator")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    
    @Autowired
    private GroupDao groupDao;
    
    @Autowired
    private I18nService i18nService;
    
    @Autowired
    private com.google.cloud.firestore.Firestore db;


    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    /**
     * Get assigned templates (Content Creator) - from time-limited assignments
     * Returns active template assignments for user's group
     */
    @GetMapping("/users/{userId}/assigned-templates")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAssignedTemplates(@PathVariable String userId,
                                                                                  @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Find user's group using GroupDao
        String userGroupId = groupDao.getUserGroupId(userId);
        
        if (userGroupId == null) {
            return ResponseEntity.ok(ApiResponse.ok(
                i18nService.getMessage("operation.success", language),
                Collections.emptyList()));
        }
        
        // Get active template assignments for this group
        List<com.example.demo.model.TemplateAssignment> assignments = 
            templateAssignmentDao.getAssignmentsByGroup(userGroupId);
        
        List<Map<String, Object>> templates = new ArrayList<>();
        for (com.example.demo.model.TemplateAssignment assignment : assignments) {
            // Skip expired assignments
            if (assignment.isExpired()) {
                continue;
            }
            
            ManualTemplate snapshot = assignment.getTemplateSnapshot();
            
            Map<String, Object> templateData = new HashMap<>();
            // Use assignment ID as template ID for mini program
            templateData.put("id", assignment.getId());
            templateData.put("masterTemplateId", assignment.getMasterTemplateId());
            templateData.put("templateTitle", snapshot.getTemplateTitle());
            templateData.put("templateDescription", snapshot.getTemplateDescription());
            templateData.put("videoPurpose", snapshot.getVideoPurpose());
            templateData.put("tone", snapshot.getTone());
            templateData.put("totalVideoLength", snapshot.getTotalVideoLength());
            templateData.put("videoFormat", snapshot.getVideoFormat());
            templateData.put("thumbnailUrl", snapshot.getThumbnailUrl());
            templateData.put("sceneCount", snapshot.getScenes() != null ? snapshot.getScenes().size() : 0);
            
            // Add assignment metadata
            templateData.put("pushedAt", assignment.getPushedAt());
            templateData.put("expiresAt", assignment.getExpiresAt());
            templateData.put("daysUntilExpiry", assignment.getDaysUntilExpiry());
            
            // Add publish status
            String publishStatus = getPublishStatus(userId, assignment.getId());
            templateData.put("publishStatus", publishStatus);
            
            templates.add(templateData);
        }
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, templates));
    }
    
    /**
     * Get publish status for user's submission of this template
     */
    private String getPublishStatus(String userId, String templateId) {
        try {
            String compositeVideoId = userId + "_" + templateId;
            com.google.cloud.firestore.DocumentSnapshot videoDoc = 
                db.collection("submittedVideos").document(compositeVideoId).get().get();
            
            if (videoDoc.exists()) {
                String status = videoDoc.getString("publishStatus");
                return status != null ? status : "pending";
            }
        } catch (Exception e) {
            log.warn("Failed to get publish status for user {} template {}: {}", 
                    userId, templateId, e.getMessage());
        }
        return "not_started";
    }


    // Inject TemplateDao for template access
    @Autowired
    private com.example.demo.dao.TemplateDao templateDao;
    
    // Get template details (Content Creator)
    @GetMapping("/templates/{templateId}")
    public ResponseEntity<ApiResponse<ManualTemplate>> getTemplateByIdForContentCreator(@PathVariable String templateId,
                                                                                          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        ManualTemplate template = templateDao.getTemplate(templateId);
        if (template != null) {
            String message = i18nService.getMessage("operation.success", language);
            return ResponseEntity.ok(ApiResponse.ok(message, template));
        } else {
            throw new NoSuchElementException("Template not found with ID: " + templateId);
        }
    }
    
}
