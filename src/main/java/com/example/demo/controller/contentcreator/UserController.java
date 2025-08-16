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


    // Get assigned templates (Content Creator) - assigned through group  
    @GetMapping("/users/{userId}/assigned-templates")
    public ResponseEntity<ApiResponse<List<ManualTemplate>>> getAssignedTemplates(@PathVariable String userId,
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
        
        // Get templates assigned to this group using TemplateDao
        List<ManualTemplate> assignedTemplates = templateDao.getTemplatesAssignedToGroup(userGroupId);
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, assignedTemplates));
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
