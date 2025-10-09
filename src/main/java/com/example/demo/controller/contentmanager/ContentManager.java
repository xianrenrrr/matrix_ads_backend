package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.UserDao;
import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.SceneSubmission;
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

    @Autowired(required = false)
    private com.example.demo.service.FirebaseStorageService firebaseStorageService;

    @Autowired
    private com.example.demo.service.TemplateCascadeDeletionService templateCascadeDeletionService;
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private com.example.demo.dao.GroupDao groupDao;
    
    // --- Submissions grouped by status ---
    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> getAllSubmissions(
            @RequestParam String managerId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Get manager's groups and their templates/members
        List<com.example.demo.model.Group> groups = groupDao.findByManagerId(managerId);
        Set<String> templateIds = new HashSet<>();
        Set<String> memberIds = new HashSet<>();
        
        for (com.example.demo.model.Group group : groups) {
            if (group.getAssignedTemplates() != null) templateIds.addAll(group.getAssignedTemplates());
            if (group.getMemberIds() != null) memberIds.addAll(group.getMemberIds());
        }
        com.google.cloud.firestore.CollectionReference submissionsRef = db.collection("submittedVideos");
        com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> querySnapshot = submissionsRef.get();
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> published = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        
        for (com.google.cloud.firestore.DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data == null) continue;
            
            // Filter by template and member
            String templateId = (String) data.get("templateId");
            String uploadedBy = (String) data.get("uploadedBy");
            if (!templateIds.contains(templateId) || !memberIds.contains(uploadedBy)) continue;
            
            // Add document ID for frontend use
            data.put("id", doc.getId());
            
            // Enrich with user information
            if (uploadedBy != null) {
                try {
                    com.google.cloud.firestore.DocumentSnapshot userDoc = db.collection("users").document(uploadedBy).get().get();
                    if (userDoc.exists()) {
                        String username = userDoc.getString("username");
                        String city = userDoc.getString("city");
                        data.put("uploaderName", username);
                        data.put("uploaderCity", city);
                    }
                } catch (Exception e) {
                    // Continue without user info if fetch fails
                }
            }
            
            // Enrich with template information
            if (templateId != null) {
                try {
                    ManualTemplate template = templateDao.getTemplate(templateId);
                    if (template != null) {
                        data.put("templateTitle", template.getTemplateTitle());
                    }
                } catch (Exception e) {
                    // Continue without template info if fetch fails
                }
            }
            
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
        
        // Mark all scenes as manual with grid overlay and validate minimum duration
        if (manualTemplate.getScenes() != null && !manualTemplate.getScenes().isEmpty()) {
            for (com.example.demo.model.Scene scene : manualTemplate.getScenes()) {
                scene.setSceneSource("manual");
                scene.setOverlayType("grid");
                
                // Validate minimum 2-second scene duration for mini app compatibility
                Long startMs = scene.getStartTimeMs();
                Long endMs = scene.getEndTimeMs();
                if (startMs != null && endMs != null) {
                    long durationMs = endMs - startMs;
                    if (durationMs < 2000) {
                        throw new IllegalArgumentException(
                            "Scene " + scene.getSceneNumber() + " (" + scene.getSceneTitle() + 
                            ") is too short (" + durationMs + "ms). Minimum duration is 2 seconds (2000ms)."
                        );
                    }
                }
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
        
        // Mark all scenes as manual with grid overlay for updates and validate minimum duration
        if (updatedTemplate.getScenes() != null) {
            for (com.example.demo.model.Scene scene : updatedTemplate.getScenes()) {
                if (scene.getSceneSource() == null) {
                    scene.setSceneSource("manual");
                }
                if (scene.getOverlayType() == null) {
                    scene.setOverlayType("grid");
                }
                
                // Validate minimum 2-second scene duration for mini app compatibility
                Long startMs = scene.getStartTimeMs();
                Long endMs = scene.getEndTimeMs();
                if (startMs != null && endMs != null) {
                    long durationMs = endMs - startMs;
                    if (durationMs < 2000) {
                        throw new IllegalArgumentException(
                            "Scene " + scene.getSceneNumber() + " (" + scene.getSceneTitle() + 
                            ") is too short (" + durationMs + "ms). Minimum duration is 2 seconds (2000ms)."
                        );
                    }
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
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable String templateId, 
                                                               @RequestParam String userId,
                                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        // Cascade delete: storage first, then Firestore docs and relationships
        templateCascadeDeletionService.deleteTemplateAssetsAndDocs(templateId);
        {
            userDao.removeCreatedTemplate(userId, templateId); // Remove templateId from created_template field in user doc
            String message = i18nService.getMessage("template.deleted", language);
            return ResponseEntity.ok(ApiResponse.ok(message));
        }
    }
    
    /**
     * Update template groups (add/remove groups from template)
     * PUT /content-manager/templates/{templateId}/groups
     */
    @PutMapping("/{templateId}/groups")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTemplateGroups(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Get the new group IDs from request
        @SuppressWarnings("unchecked")
        List<String> newGroupIds = (List<String>) requestBody.get("groupIds");
        if (newGroupIds == null) {
            throw new IllegalArgumentException("groupIds list is required");
        }
        
        // Check if template exists
        ManualTemplate template = templateDao.getTemplate(templateId);
        if (template == null) {
            throw new NoSuchElementException("Template not found with ID: " + templateId);
        }
        
        // Update template groups using the service
        templateGroupService.updateTemplateGroups(templateId, newGroupIds);
        
        // Get updated template to return current state
        ManualTemplate updatedTemplate = templateDao.getTemplate(templateId);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("templateId", templateId);
        responseData.put("templateTitle", updatedTemplate.getTemplateTitle());
        responseData.put("groupIds", updatedTemplate.getAssignedGroups());
        responseData.put("groupCount", updatedTemplate.getAssignedGroups() != null ? updatedTemplate.getAssignedGroups().size() : 0);
        
        String message = i18nService.getMessage("template.groups.updated", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }
    
    /**
     * Get submitted video data by composite ID (userId_templateId)
     * GET /content-manager/templates/submitted-videos/{compositeVideoId}
     */
    @GetMapping("/submitted-videos/{compositeVideoId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubmittedVideo(@PathVariable String compositeVideoId,
                                                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Get video document from submittedVideos collection
        com.google.cloud.firestore.DocumentSnapshot videoDoc = db.collection("submittedVideos").document(compositeVideoId).get().get();
        
        if (!videoDoc.exists()) {
            throw new NoSuchElementException("No submission found for ID: " + compositeVideoId);
        }
        
        Map<String, Object> videoData = new HashMap<>(videoDoc.getData());
        
        // Fetch full scene details from sceneSubmissions collection using scene IDs
        @SuppressWarnings("unchecked")
        Map<String, Object> scenes = (Map<String, Object>) videoData.get("scenes");
        if (scenes != null) {
            Map<String, Object> fullScenes = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : scenes.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sceneRef = (Map<String, Object>) entry.getValue();
                String sceneId = (String) sceneRef.get("sceneId");
                
                if (sceneId != null) {
                    SceneSubmission sceneSubmission = sceneSubmissionDao.findById(sceneId);
                    if (sceneSubmission != null) {
                        Map<String, Object> fullSceneData = new HashMap<>();
                        fullSceneData.put("sceneId", sceneSubmission.getId());
                        fullSceneData.put("sceneNumber", sceneSubmission.getSceneNumber());
                        fullSceneData.put("sceneTitle", sceneSubmission.getSceneTitle());
                        fullSceneData.put("videoUrl", sceneSubmission.getVideoUrl());
                        // Attach a short-lived signed URL for preview/streaming in manager UI
                        try {
                            if (firebaseStorageService != null && sceneSubmission.getVideoUrl() != null) {
                                String signed = firebaseStorageService.generateSignedUrl(sceneSubmission.getVideoUrl());
                                fullSceneData.put("videoSignedUrl", signed);
                            }
                        } catch (Exception ignored) {}
                        fullSceneData.put("thumbnailUrl", sceneSubmission.getThumbnailUrl());
                        fullSceneData.put("status", sceneSubmission.getStatus());
                        fullSceneData.put("similarityScore", sceneSubmission.getSimilarityScore());
                        fullSceneData.put("aiSuggestions", sceneSubmission.getAiSuggestions());
                        fullSceneData.put("submittedAt", sceneSubmission.getSubmittedAt());
                        fullSceneData.put("originalFileName", sceneSubmission.getOriginalFileName());
                        fullSceneData.put("fileSize", sceneSubmission.getFileSize());
                        fullSceneData.put("format", sceneSubmission.getFormat());
                        fullScenes.put(entry.getKey(), fullSceneData);
                    } else {
                        fullScenes.put(entry.getKey(), sceneRef);
                    }
                }
            }
            videoData.put("scenes", fullScenes);
        }
        // If compiledVideoUrl exists, attach a signed URL for client download
        try {
            Object compiledUrl = videoData.get("compiledVideoUrl");
            if (compiledUrl instanceof String && firebaseStorageService != null) {
                String signed = firebaseStorageService.generateSignedUrl((String) compiledUrl);
                videoData.put("compiledVideoSignedUrl", signed);
            }
        } catch (Exception ignored) {}

        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, videoData));
    }
    
    // --- NEW: Manual Template Creation with AI Scene Analysis ---
    
    @Autowired
    private com.example.demo.ai.services.SceneAnalysisService sceneAnalysisService;
    
    @Autowired
    private com.example.demo.dao.VideoDao videoDao;
    
    /**
     * Create manual template with AI analysis for each scene video.
     * Each uploaded video is analyzed as ONE complete scene (no scene detection/cutting).
     * 
     * POST /content-manager/templates/manual-with-ai
     */
    @PostMapping(value = "/manual-with-ai", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createManualTemplateWithAI(
        @RequestParam("userId") String userId,
        @RequestParam("templateTitle") String templateTitle,
        @RequestParam(value = "templateDescription", required = false) String templateDescription,
        @RequestParam("scenesMetadata") String scenesMetadataJson,
        @RequestParam(value = "selectedGroupIds", required = false) String selectedGroupIdsJson,
        @RequestParam Map<String, org.springframework.web.multipart.MultipartFile> videoFiles,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        log.info("Creating manual template with AI analysis for user: {}", userId);
        
        // Parse scenes metadata
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<SceneMetadata> scenesMetadata = mapper.readValue(
            scenesMetadataJson, 
            new com.fasterxml.jackson.core.type.TypeReference<List<SceneMetadata>>() {}
        );
        
        // Parse group IDs
        List<String> selectedGroupIds = null;
        if (selectedGroupIdsJson != null && !selectedGroupIdsJson.trim().isEmpty()) {
            selectedGroupIds = mapper.readValue(
                selectedGroupIdsJson, 
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
            );
        }
        
        log.info("Processing {} scenes for template: {}", scenesMetadata.size(), templateTitle);
        
        // Process each scene video
        List<com.example.demo.model.Scene> aiAnalyzedScenes = new ArrayList<>();
        
        for (SceneMetadata metadata : scenesMetadata) {
            String videoKey = "sceneVideo_" + metadata.getSceneNumber();
            org.springframework.web.multipart.MultipartFile videoFile = videoFiles.get(videoKey);
            
            if (videoFile == null) {
                throw new IllegalArgumentException(
                    "Missing video file for scene " + metadata.getSceneNumber()
                );
            }
            
            log.info("Processing scene {}: {}", metadata.getSceneNumber(), metadata.getSceneTitle());
            
            // 1. Upload video (REUSE existing code)
            String videoId = java.util.UUID.randomUUID().toString();
            com.example.demo.service.FirebaseStorageService.UploadResult uploadResult = 
                firebaseStorageService.uploadVideoWithThumbnail(videoFile, userId, videoId);
            
            // 2. Create Video object (REUSE existing code)
            com.example.demo.model.Video video = new com.example.demo.model.Video();
            video.setId(videoId);
            video.setUserId(userId);
            video.setUrl(uploadResult.videoUrl);
            video.setThumbnailUrl(uploadResult.thumbnailUrl);
            videoDao.saveVideo(video);
            
            // 3. Analyze as single scene (NEW - no scene detection!)
            com.example.demo.model.Scene aiScene = sceneAnalysisService.analyzeSingleScene(
                video,
                language,
                metadata.getSceneDescription()
            );
            
            // 4. Set user-provided metadata
            aiScene.setSceneNumber(metadata.getSceneNumber());
            aiScene.setSceneTitle(metadata.getSceneTitle());
            aiScene.setSceneDescription(metadata.getSceneDescription());
            
            aiAnalyzedScenes.add(aiScene);
            log.info("Scene {} analyzed successfully with overlay type: {}", 
                     metadata.getSceneNumber(), aiScene.getOverlayType());
        }
        
        // 5. Create template (REUSE existing code)
        ManualTemplate template = new ManualTemplate();
        template.setUserId(userId);
        template.setTemplateTitle(templateTitle);
        template.setTemplateDescription(templateDescription);
        template.setScenes(aiAnalyzedScenes);
        template.setLocaleUsed(language);
        
        // 6. Save with groups (REUSE existing code)
        String templateId = templateGroupService.createTemplateWithGroups(template, selectedGroupIds);
        userDao.addCreatedTemplate(userId, templateId);
        
        log.info("Manual template created successfully: {} with {} scenes", templateId, aiAnalyzedScenes.size());
        
        // 7. Response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("templateId", templateId);
        responseData.put("template", template);
        responseData.put("scenesAnalyzed", aiAnalyzedScenes.size());
        
        String message = i18nService.getMessage("template.created", language);
        if (selectedGroupIds != null && !selectedGroupIds.isEmpty()) {
            message += " and assigned to " + selectedGroupIds.size() + " groups";
        }
        
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }
    
    /**
     * Helper class for scene metadata in manual template creation
     */
    public static class SceneMetadata {
        private int sceneNumber;
        private String sceneTitle;
        private String sceneDescription;
        
        public SceneMetadata() {}
        
        public int getSceneNumber() { return sceneNumber; }
        public void setSceneNumber(int sceneNumber) { this.sceneNumber = sceneNumber; }
        
        public String getSceneTitle() { return sceneTitle; }
        public void setSceneTitle(String sceneTitle) { this.sceneTitle = sceneTitle; }
        
        public String getSceneDescription() { return sceneDescription; }
        public void setSceneDescription(String sceneDescription) { 
            this.sceneDescription = sceneDescription; 
        }
    }
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContentManager.class);
}
// Change Log: Manual scenes always set sceneSource="manual" and overlayType="grid" for dual scene system
// Added manual-with-ai endpoint for creating templates with per-scene AI analysis (no scene detection)
