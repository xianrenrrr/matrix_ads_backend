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
    
    // Removed getAssignedTemplates - now using GroupController.getGroupTemplates instead
    
    // Removed getPublishStatus - no longer needed


    // No longer need TemplateDao - using assignments only
    
    // Get template details (Content Creator) - using assignment ID only
    @GetMapping("/templates/{assignmentId}")
    public ResponseEntity<ApiResponse<ManualTemplate>> getTemplateByIdForContentCreator(@PathVariable String assignmentId,
                                                                                          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Get template from assignment
        com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
        if (assignment == null || assignment.getTemplateSnapshot() == null) {
            throw new NoSuchElementException("Template assignment not found with ID: " + assignmentId);
        }
        
        ManualTemplate template = assignment.getTemplateSnapshot();
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, template));
    }
    
}
