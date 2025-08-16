package com.example.demo.controller.contentcreator;

import com.example.demo.model.ManualTemplate;
import com.example.demo.dao.UserDao;
import com.example.demo.dao.GroupDao;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-creator")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private Firestore db;
    
    @Autowired
    private UserDao userDao;
    
    @Autowired
    private GroupDao groupDao;
    
    @Autowired
    private I18nService i18nService;


    // Get subscribed templates (Content Creator)
    @GetMapping("/users/{userId}/subscribed-templates")
    public ResponseEntity<ApiResponse<List<ManualTemplate>>> getSubscribedTemplates(@PathVariable String userId,
                                                                                     @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        // Get user's subscribed templates using UserDao
        Map<String, Boolean> subscribedTemplatesMap = userDao.getSubscribedTemplates(userId);
        
        List<ManualTemplate> templates = new ArrayList<>();
        
        // Get template details for each subscribed template
        for (String templateId : subscribedTemplatesMap.keySet()) {
            if (Boolean.TRUE.equals(subscribedTemplatesMap.get(templateId))) {
                DocumentReference templateRef = db.collection("templates").document(templateId);
                DocumentSnapshot templateSnap = templateRef.get().get();
                if (templateSnap.exists()) {
                    ManualTemplate template = templateSnap.toObject(ManualTemplate.class);
                    if (template != null) {
                        template.setId(templateId); // Ensure ID is set
                        templates.add(template);
                    }
                } else {
                    // Remove non-existent template from user's subscriptions
                    userDao.removeSubscribedTemplate(userId, templateId);
                }
            }
        }
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, templates));
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
    
    /**
     * Get templates available to user through their group
     * User's group is determined by accepted invites
     */
    @GetMapping("/users/{userId}/available-templates")
    public ResponseEntity<ApiResponse<List<AvailableTemplate>>> getAvailableTemplates(
            @PathVariable String userId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Find user's group using GroupDao
        String userGroupId = groupDao.getUserGroupId(userId);
        if (userGroupId == null) {
            log.info("User {} has no group", userId);
            return ResponseEntity.ok(ApiResponse.ok(
                i18nService.getMessage("operation.success", language),
                Collections.emptyList()));
        }
        
        // Get templates assigned to this group
        List<ManualTemplate> groupTemplates = getTemplatesForGroup(userGroupId);
        
        // Get user's subscription status
        Map<String, Boolean> subscribed = userDao.getSubscribedTemplates(userId);
        
        // Build response with subscription status
        List<AvailableTemplate> result = new ArrayList<>();
        for (ManualTemplate template : groupTemplates) {
            AvailableTemplate at = new AvailableTemplate();
            at.setTemplate(template);
            at.setGroupId(userGroupId);
            at.setSubscribed(subscribed.getOrDefault(template.getId(), false));
            result.add(at);
        }
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }
    
    /**
     * Subscribe to a template (explicit user action)
     */
    @PostMapping("/users/{userId}/subscribe/{templateId}")
    public ResponseEntity<ApiResponse<String>> subscribeToTemplate(
            @PathVariable String userId,
            @PathVariable String templateId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Verify template exists
        ManualTemplate template = templateDao.getTemplate(templateId);
        if (template == null) {
            throw new NoSuchElementException("Template not found: " + templateId);
        }
        
        // User explicitly subscribes
        userDao.addSubscribedTemplate(userId, templateId);
        log.info("User {} subscribed to template {}", userId, templateId);
        
        String message = i18nService.getMessage("template.subscribed", language);
        return ResponseEntity.ok(ApiResponse.ok(message, "Subscribed successfully"));
    }
    
    /**
     * Unsubscribe from a template
     */
    @DeleteMapping("/users/{userId}/subscribe/{templateId}")
    public ResponseEntity<ApiResponse<String>> unsubscribeFromTemplate(
            @PathVariable String userId,
            @PathVariable String templateId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        userDao.removeSubscribedTemplate(userId, templateId);
        log.info("User {} unsubscribed from template {}", userId, templateId);
        
        String message = i18nService.getMessage("template.unsubscribed", language);
        return ResponseEntity.ok(ApiResponse.ok(message, "Unsubscribed successfully"));
    }
    
    
    /**
     * Get templates assigned to a group
     */
    private List<ManualTemplate> getTemplatesForGroup(String groupId) throws Exception {
        // Query templates that have this group in their assignedGroups
        Query query = db.collection("templates")
            .whereArrayContains("assignedGroups", groupId);
            
        QuerySnapshot snapshot = query.get().get();
        List<ManualTemplate> templates = new ArrayList<>();
        
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            ManualTemplate template = doc.toObject(ManualTemplate.class);
            if (template != null) {
                template.setId(doc.getId());
                templates.add(template);
            }
        }
        
        return templates;
    }
    
    /**
     * DTO for available template with subscription status
     */
    public static class AvailableTemplate {
        private ManualTemplate template;
        private String groupId;
        private boolean subscribed;
        
        public ManualTemplate getTemplate() { return template; }
        public void setTemplate(ManualTemplate template) { this.template = template; }
        
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        
        public boolean isSubscribed() { return subscribed; }
        public void setSubscribed(boolean subscribed) { this.subscribed = subscribed; }
    }
}
