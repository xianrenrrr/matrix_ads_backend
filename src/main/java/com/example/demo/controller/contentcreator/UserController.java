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


    // Get assigned templates (Content Creator) - assigned through group  
    // Returns minimal data for fast loading: id, title, sceneCount, duration, thumbnail, publishStatus
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
        
        // Get lightweight template summaries (only essential fields from DB)
        List<Map<String, Object>> templateSummaries = templateDao.getTemplateSummariesForGroup(userGroupId);
        
        // Add publish status for each template
        for (Map<String, Object> summary : templateSummaries) {
            String templateId = (String) summary.get("id");
            String publishStatus = getPublishStatus(userId, templateId);
            summary.put("publishStatus", publishStatus);
        }
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, templateSummaries));
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
