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

    /**
     * Get template by assignment ID for content creator
     * This endpoint is used by mini app when recording scenes
     * It fetches the template snapshot from templateAssignments collection
     */
    @GetMapping("/templates/{assignmentId}")
    public ResponseEntity<ApiResponse<ManualTemplate>> getTemplateByAssignmentId(
            @PathVariable String assignmentId) {
        try {
            log.info("Fetching template for assignment ID: {}", assignmentId);
            
            // Get the assignment which contains the template snapshot
            com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
            
            if (assignment == null) {
                log.warn("Assignment not found: {}", assignmentId);
                return ResponseEntity.status(404)
                    .body(ApiResponse.error(i18nService.getMessage("assignment.notFound", "Assignment not found")));
            }
            
            ManualTemplate template = assignment.getTemplateSnapshot();
            
            if (template == null) {
                log.warn("Template snapshot not found in assignment: {}", assignmentId);
                return ResponseEntity.status(404)
                    .body(ApiResponse.error(i18nService.getMessage("template.notFound", "Template not found")));
            }
            
            log.info("Successfully retrieved template for assignment: {}", assignmentId);
            return ResponseEntity.ok(ApiResponse.ok(
                i18nService.getMessage("template.retrieved", "Template retrieved successfully"), 
                template
            ));
            
        } catch (Exception e) {
            log.error("Error fetching template for assignment {}: {}", assignmentId, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error(i18nService.getMessage("error.internal", "Internal server error")));
        }
    }
    
}
