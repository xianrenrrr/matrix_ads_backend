package com.example.demo.controller.contentcreator;

import com.example.demo.model.ManualTemplate;
import com.example.demo.dao.UserDao;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-creator")
public class UserController {
    @Autowired
    private Firestore db;
    
    @Autowired
    private UserDao userDao;
    
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
}
