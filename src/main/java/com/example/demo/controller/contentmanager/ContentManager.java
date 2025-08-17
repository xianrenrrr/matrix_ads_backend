package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.UserDao;
import com.example.demo.model.ManualTemplate;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import com.example.demo.service.TemplateGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/content-manager/templates")
public class ContentManager {
    @Autowired
    private com.google.cloud.firestore.Firestore db;
    
    @Autowired
    private I18nService i18nService;
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private UserDao userDao;
    
    @Autowired
    private TemplateGroupService templateGroupService;
    
    // --- Submissions grouped by status ---
    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> getAllSubmissions(@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        com.google.cloud.firestore.CollectionReference submissionsRef = db.collection("submittedVideos");
        com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> querySnapshot = submissionsRef.get();
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> published = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        
        for (com.google.cloud.firestore.DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data == null) continue;
            
            // Add document ID for frontend use
            data.put("id", doc.getId());
            
            String status = (String) data.getOrDefault("publishStatus", "pending");
            if ("approved".equalsIgnoreCase(status)) {
                approved.add(data);
            } else if ("published".equalsIgnoreCase(status)) {
                published.add(data);
            } else if ("rejected".equalsIgnoreCase(status)) {
                rejected.add(data);
            } else {
                pending.add(data);
            }
        }
        
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("published", published);
        result.put("rejected", rejected);
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTemplate(@RequestBody com.example.demo.model.CreateTemplateRequest request,
                                                                            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        String userId = request.getUserId();
        ManualTemplate manualTemplate = request.getManualTemplate();
        List<String> selectedGroupIds = request.getSelectedGroupIds();
        
        manualTemplate.setUserId(userId);
        
        // Mark all scenes as manual with grid overlay
        if (manualTemplate.getScenes() != null) {
            for (com.example.demo.model.Scene scene : manualTemplate.getScenes()) {
                scene.setSceneSource("manual");
                scene.setOverlayType("grid");
            }
        }
        
        // Create the template using the service wrapper
        String templateId = templateGroupService.createTemplateWithGroups(manualTemplate, selectedGroupIds);
        userDao.addCreatedTemplate(userId, templateId);
        
        List<String> assignedGroupNames = selectedGroupIds != null ? selectedGroupIds : new ArrayList<>();
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("template", manualTemplate);
        responseData.put("templateId", templateId);
        responseData.put("assignedGroups", assignedGroupNames);
        
        String message = i18nService.getMessage("template.created", language);
        if (!assignedGroupNames.isEmpty()) {
            message += " and made available to " + assignedGroupNames.size() + " groups";
        }
        
        return new ResponseEntity<>(ApiResponse.ok(message, responseData), HttpStatus.CREATED);
    }
    

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<TemplateSummary>>> getTemplatesByUserId(@PathVariable String userId,
                                                                                      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        List<ManualTemplate> templates = templateDao.getTemplatesByUserId(userId);
        List<TemplateSummary> summaries = templates.stream()
            .map(t -> new TemplateSummary(t.getId(), t.getTemplateTitle()))
            .toList();
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, summaries));
    }

    // DTO for summary
    public static class TemplateSummary {
        private String id;
        private String templateTitle;
        public TemplateSummary(String id, String templateTitle) {
            this.id = id;
            this.templateTitle = templateTitle;
        }
        public String getId() { return id; }
        public String getTemplateTitle() { return templateTitle; }
        public void setId(String id) { this.id = id; }
        public void setTemplateTitle(String templateTitle) { this.templateTitle = templateTitle; }
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<ManualTemplate>> getTemplateById(@PathVariable String templateId,
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

    @PutMapping("/{templateId}")
    public ResponseEntity<ApiResponse<ManualTemplate>> updateTemplate(
            @PathVariable String templateId, 
            @RequestBody ManualTemplate updatedTemplate,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        updatedTemplate.setId(templateId); // Ensure ID matches path parameter
        
        // Mark all scenes as manual with grid overlay for updates
        if (updatedTemplate.getScenes() != null) {
            for (com.example.demo.model.Scene scene : updatedTemplate.getScenes()) {
                if (scene.getSceneSource() == null) {
                    scene.setSceneSource("manual");
                }
                if (scene.getOverlayType() == null) {
                    scene.setOverlayType("grid");
                }
            }
        }
        
        boolean updated = templateDao.updateTemplate(templateId, updatedTemplate);
        
        if (updated) {
            String message = i18nService.getMessage("template.updated", language);
            return ResponseEntity.ok(ApiResponse.ok(message, updatedTemplate));
        } else {
            throw new NoSuchElementException("Template not found with ID: " + templateId);
        }
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<ApiResponse<String>> deleteTemplate(@PathVariable String templateId, 
                                                               @RequestParam String userId,
                                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        // Use service wrapper to delete template and clean up group relationships
        boolean deleted = templateGroupService.deleteTemplateWithCleanup(templateId);
        if (deleted) {
            userDao.removeCreatedTemplate(userId, templateId); // Remove templateId from created_template field in user doc
            String message = i18nService.getMessage("template.deleted", language);
            return ResponseEntity.ok(ApiResponse.ok(message, "Template deleted successfully"));
        } else {
            throw new NoSuchElementException("Template not found with ID: " + templateId);
        }
    }
}
// Change Log: Manual scenes always set sceneSource="manual" and overlayType="grid" for dual scene system

