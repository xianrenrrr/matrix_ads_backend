package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.UserDao;
import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.SceneSubmission;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;

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
    


    @Autowired(required = false)
    private com.example.demo.service.FirebaseStorageService firebaseStorageService;

    @Autowired
    private com.example.demo.service.TemplateCascadeDeletionService templateCascadeDeletionService;
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private com.example.demo.dao.GroupDao groupDao;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    /**
     * Get manager's groups
     * GET /content-manager/templates/manager/{managerId}/groups
     */
    @GetMapping("/manager/{managerId}/groups")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getManagerGroups(
            @PathVariable String managerId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        try {
            List<com.example.demo.model.Group> groups = groupDao.findByManagerId(managerId);
            
            List<Map<String, Object>> groupList = new ArrayList<>();
            for (com.example.demo.model.Group group : groups) {
                Map<String, Object> groupData = new HashMap<>();
                groupData.put("id", group.getId());
                groupData.put("name", group.getGroupName());
                groupData.put("status", group.getStatus());
                groupData.put("memberCount", group.getMemberIds() != null ? group.getMemberIds().size() : 0);
                groupData.put("createdAt", group.getCreatedAt());
                groupList.add(groupData);
            }
            
            return ResponseEntity.ok(ApiResponse.ok(
                i18nService.getMessage("groups.fetch.success", language), groupList));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(i18nService.getMessage("groups.fetch.error", language)));
        }
    }
    
    // --- Submissions grouped by status ---
    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> getAllSubmissions(
            @RequestParam String managerId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Get manager's groups and their assignment IDs
        List<com.example.demo.model.Group> groups = groupDao.findByManagerId(managerId);
        Set<String> assignmentIds = new HashSet<>();
        Set<String> memberIds = new HashSet<>();
        
        for (com.example.demo.model.Group group : groups) {
            // Get active assignments for this group
            try {
                List<com.example.demo.model.TemplateAssignment> assignments = 
                    templateAssignmentDao.getAssignmentsByGroup(group.getId());
                for (com.example.demo.model.TemplateAssignment assignment : assignments) {
                    if (!assignment.isExpired()) {
                        assignmentIds.add(assignment.getId());
                    }
                }
            } catch (Exception e) {
                // Continue if assignment lookup fails
            }
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
            
            // Filter by assignment ID and member (NEW SYSTEM)
            String assignmentId = (String) data.get("assignmentId");  // NEW: use assignmentId instead of templateId
            String templateId = (String) data.get("templateId");      // LEGACY: fallback for old submissions
            String uploadedBy = (String) data.get("uploadedBy");
            
            // Check if this submission belongs to manager's assignments
            boolean belongsToManager = false;
            if (assignmentId != null && assignmentIds.contains(assignmentId)) {
                belongsToManager = true;  // NEW system
            } else if (templateId != null) {
                // LEGACY: check if templateId matches any assignment's masterTemplateId
                for (com.example.demo.model.Group group : groups) {
                    try {
                        List<com.example.demo.model.TemplateAssignment> assignments = 
                            templateAssignmentDao.getAssignmentsByGroup(group.getId());
                        for (com.example.demo.model.TemplateAssignment assignment : assignments) {
                            if (templateId.equals(assignment.getMasterTemplateId()) && !assignment.isExpired()) {
                                belongsToManager = true;
                                break;
                            }
                        }
                        if (belongsToManager) break;
                    } catch (Exception e) {
                        // Continue if assignment lookup fails
                    }
                }
            }
            
            if (!belongsToManager || !memberIds.contains(uploadedBy)) continue;
            
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
            ManualTemplate template = null;
            if (assignmentId != null) {
                // NEW: Get template from assignment snapshot
                try {
                    com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
                    if (assignment != null) {
                        template = assignment.getTemplateSnapshot();
                    }
                } catch (Exception e) {
                    // Continue without template info if fetch fails
                }
            } else if (templateId != null) {
                // LEGACY: Get template directly
                try {
                    template = templateDao.getTemplate(templateId);
                } catch (Exception e) {
                    // Continue without template info if fetch fails
                }
            }
            
            if (template != null) {
                data.put("templateTitle", template.getTemplateTitle());
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
        
        manualTemplate.setUserId(userId);
        
        // Set thumbnail from first scene's keyframe
        if (manualTemplate.getScenes() != null && !manualTemplate.getScenes().isEmpty()) {
            Scene firstScene = manualTemplate.getScenes().get(0);
            if (firstScene.getKeyframeUrl() != null && !firstScene.getKeyframeUrl().isEmpty()) {
                manualTemplate.setThumbnailUrl(firstScene.getKeyframeUrl());
            }
        }
        
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
        
        // Create the template (groups are now assigned via push button with TemplateAssignment)
        String templateId = templateDao.createTemplate(manualTemplate);
        userDao.addCreatedTemplate(userId, templateId);
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("template", manualTemplate);
        responseData.put("templateId", templateId);
        // Legacy: assignedGroups deprecated, return empty for backward compatibility
        responseData.put("assignedGroups", new ArrayList<>());
        
        String message = i18nService.getMessage("template.created", language);
        
        return new ResponseEntity<>(ApiResponse.ok(message, responseData), HttpStatus.CREATED);
    }
    

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<com.example.demo.model.TemplateSummary>>> getTemplatesByUserId(@PathVariable String userId,
                                                                                      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        List<ManualTemplate> templates = templateDao.getTemplatesByUserId(userId);
        
        // Convert to lightweight DTO with only essential fields
        List<com.example.demo.model.TemplateSummary> summaries = templates.stream()
            .map(com.example.demo.model.TemplateSummary::fromManualTemplate)
            .collect(java.util.stream.Collectors.toList());
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, summaries));
    }

    /**
     * Get assigned groups for a template
     * GET /content-manager/templates/{templateId}/groups
     * NOTE: This must come BEFORE @GetMapping("/{templateId}") to avoid path matching conflicts
     */
    @GetMapping("/{templateId}/groups")
    public ResponseEntity<ApiResponse<List<String>>> getTemplateGroups(
            @PathVariable String templateId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Check if template exists
        ManualTemplate template = templateDao.getTemplate(templateId);
        if (template == null) {
            throw new NoSuchElementException("Template not found with ID: " + templateId);
        }
        
        // Legacy: assignedGroups field deprecated, now using TemplateAssignment
        // Return empty list for backward compatibility
        List<String> assignedGroups = new ArrayList<>();
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, assignedGroups));
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
        
        // Legacy: Group assignment now done via push button with TemplateAssignment
        // This endpoint is deprecated - use push/delete assignment buttons instead
        
        // Get updated template to return current state
        ManualTemplate updatedTemplate = templateDao.getTemplate(templateId);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("templateId", templateId);
        responseData.put("templateTitle", updatedTemplate.getTemplateTitle());
        // Legacy: assignedGroups deprecated, return empty for backward compatibility
        responseData.put("groupIds", new ArrayList<>());
        responseData.put("groupCount", 0);
        
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
    
    // SceneAnalysisService removed - using UnifiedSceneAnalysisService
    
    @Autowired
    private com.example.demo.dao.VideoDao videoDao;
    
    @Autowired
    private com.example.demo.ai.label.ObjectLabelService objectLabelService;
    
    @Autowired
    private com.example.demo.ai.services.VideoMetadataService videoMetadataService;
    
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
        @RequestParam(value = "folderId", required = false) String folderId,
        @RequestParam("scenesMetadata") String scenesMetadataJson,
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
            // TODO: Replace with UnifiedSceneAnalysisService
            com.example.demo.model.Scene aiScene = new com.example.demo.model.Scene();
            aiScene.setSceneSource("manual");
            
            // 4. Set user-provided metadata
            aiScene.setSceneNumber(metadata.getSceneNumber());
            aiScene.setSceneTitle(metadata.getSceneTitle());
            aiScene.setSceneDescription(metadata.getSceneDescription());
            
            // 5. IMPORTANT: Set videoId so mini app can fetch the scene video
            aiScene.setVideoId(videoId);
            
            aiAnalyzedScenes.add(aiScene);
            log.info("Scene {} analyzed successfully with overlay type: {}", 
                     metadata.getSceneNumber(), aiScene.getOverlayType());
        }
        
        // 5. Create template with calculated metadata
        ManualTemplate template = new ManualTemplate();
        template.setUserId(userId);
        template.setTemplateTitle(templateTitle);
        template.setTemplateDescription(templateDescription);
        template.setScenes(aiAnalyzedScenes);
        template.setLocaleUsed(language);
        
        // Set folderId if provided
        if (folderId != null && !folderId.isBlank()) {
            template.setFolderId(folderId);
            log.info("Setting folderId: {} for manual template", folderId);
        }
        
        // Calculate total video length (sum of all scene durations)
        int totalDuration = aiAnalyzedScenes.stream()
            .mapToInt(scene -> (int) scene.getSceneDurationInSeconds())
            .sum();
        template.setTotalVideoLength(totalDuration);
        
        // 6. Generate AI metadata using reasoning model (sets deviceOrientation)
        log.info("Generating AI metadata for manual template with {} scenes", aiAnalyzedScenes.size());
        generateManualTemplateMetadata(template, aiAnalyzedScenes, language, templateDescription);
        
        // 7. Derive video format from first scene's device orientation (AFTER AI metadata)
        log.info("=== DERIVING VIDEO FORMAT from first scene ===");
        String videoFormat = "1080p 16:9"; // Fallback
        
        if (!aiAnalyzedScenes.isEmpty()) {
            com.example.demo.model.Scene firstScene = aiAnalyzedScenes.get(0);
            String aspectRatio = firstScene.getDeviceOrientation();  // e.g., "9:16" or "16:9"
            
            if (aspectRatio != null && !aspectRatio.isEmpty()) {
                // Assume 1080p resolution (most common for phone videos)
                videoFormat = "1080p " + aspectRatio;
                log.info("✅ SUCCESS: Derived video format from first scene: {}", videoFormat);
            } else {
                log.warn("❌ FAILED: First scene has no deviceOrientation, using fallback: {}", videoFormat);
            }
        } else {
            log.warn("❌ FAILED: No scenes in template, using fallback: {}", videoFormat);
        }
        
        template.setVideoFormat(videoFormat);
        log.info("Template video format set to: {}", videoFormat);
        
        // Set thumbnail from first scene's keyframe
        if (!aiAnalyzedScenes.isEmpty()) {
            com.example.demo.model.Scene firstScene = aiAnalyzedScenes.get(0);
            if (firstScene.getKeyframeUrl() != null && !firstScene.getKeyframeUrl().isEmpty()) {
                template.setThumbnailUrl(firstScene.getKeyframeUrl());
            }
        }
        
        // 8. Save template (groups are now assigned via push button with TemplateAssignment)
        String templateId = templateDao.createTemplate(template);
        userDao.addCreatedTemplate(userId, templateId);
        
        log.info("Manual template created successfully: {} with {} scenes", templateId, aiAnalyzedScenes.size());
        
        // 9. Response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("templateId", templateId);
        responseData.put("template", template);
        responseData.put("scenesAnalyzed", aiAnalyzedScenes.size());
        
        String message = i18nService.getMessage("template.created", language);
        
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
    
    /**
     * Generate AI metadata for manual template (SAME logic as AI template)
     * Uses reasoning model to analyze all scenes and generate template-level metadata
     */
    private void generateManualTemplateMetadata(
            ManualTemplate template, 
            List<com.example.demo.model.Scene> scenes, 
            String language,
            String userDescription) {
        try {
            log.info("Generating AI metadata for manual template with {} scenes", scenes.size());
            
            // Build payload for reasoning model (SAME as AI template)
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> tpl = new HashMap<>();
            tpl.put("videoTitle", template.getTemplateTitle());
            tpl.put("language", language);
            tpl.put("totalDurationSeconds", template.getTotalVideoLength());
            tpl.put("videoFormat", template.getVideoFormat());
            if (userDescription != null && !userDescription.isBlank()) {
                tpl.put("userDescription", userDescription);
            }
            payload.put("template", tpl);

            // Collect scene data with detected objects
            List<Map<String, Object>> sceneArr = new ArrayList<>();
            for (com.example.demo.model.Scene s : scenes) {
                Map<String, Object> so = new HashMap<>();
                so.put("sceneNumber", s.getSceneNumber());
                so.put("durationSeconds", s.getSceneDurationInSeconds());
                so.put("keyframeUrl", s.getKeyframeUrl());
                
                // Collect detected object labels (Chinese) for context
                List<String> labels = new ArrayList<>();
                if (s.getOverlayPolygons() != null) {
                    for (var p : s.getOverlayPolygons()) {
                        if (p.getLabelLocalized() != null && !p.getLabelLocalized().isEmpty()) {
                            labels.add(p.getLabelLocalized());
                        } else if (p.getLabelZh() != null && !p.getLabelZh().isEmpty()) {
                            labels.add(p.getLabelZh());
                        }
                    }
                }
                if (s.getOverlayObjects() != null) {
                    for (var o : s.getOverlayObjects()) {
                        if (o.getLabelLocalized() != null && !o.getLabelLocalized().isEmpty()) {
                            labels.add(o.getLabelLocalized());
                        } else if (o.getLabelZh() != null && !o.getLabelZh().isEmpty()) {
                            labels.add(o.getLabelZh());
                        }
                    }
                }
                if (labels.size() > 5) labels = labels.subList(0, 5);
                so.put("detectedObjects", labels);
                
                // Include user-provided scene description
                if (s.getSceneDescription() != null && !s.getSceneDescription().isEmpty()) {
                    so.put("sceneDescription", s.getSceneDescription());
                }
                
                sceneArr.add(so);
            }
            payload.put("scenes", sceneArr);

            // Call reasoning model via ObjectLabelService
            Map<String, Object> result = objectLabelService.generateTemplateGuidance(payload);
            if (result == null || result.isEmpty()) {
                log.info("AI guidance unavailable; leaving metadata/guidance empty");
                return;
            }

            // Apply template-level metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> t = (Map<String, Object>) result.get("template");
            if (t != null) {
                Object vp = t.get("videoPurpose"); 
                if (vp instanceof String s) template.setVideoPurpose(trim40(s));
                
                Object tone = t.get("tone"); 
                if (tone instanceof String s) template.setTone(trim40(s));
                
                Object light = t.get("lightingRequirements"); 
                if (light instanceof String s) template.setLightingRequirements(trim60(s));
                
                Object bgm = t.get("backgroundMusic"); 
                if (bgm instanceof String s) template.setBackgroundMusic(trim40(s));
            }

            // Apply per-scene guidance
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rs = (List<Map<String, Object>>) result.get("scenes");
            if (rs != null) {
                Map<Integer, Map<String, Object>> byNum = new HashMap<>();
                for (Map<String, Object> one : rs) {
                    Object n = one.get("sceneNumber");
                    if (n instanceof Number num) byNum.put(num.intValue(), one);
                }
                
                for (com.example.demo.model.Scene s : scenes) {
                    Map<String, Object> one = byNum.get(s.getSceneNumber());
                    if (one == null) continue;
                    
                    // Apply AI-generated scene guidance
                    Object scriptLine = one.get("scriptLine");
                    if (scriptLine instanceof String v && !v.isBlank()) {
                        s.setScriptLine(v);
                    }
                    
                    Object person = one.get("presenceOfPerson"); 
                    if (person instanceof Boolean b) s.setPresenceOfPerson(b);
                    
                    Object move = one.get("movementInstructions"); 
                    if (move instanceof String v) s.setMovementInstructions(trim60(v));
                    
                    Object bg = one.get("backgroundInstructions"); 
                    if (bg instanceof String v) s.setBackgroundInstructions(trim60(v));
                    
                    Object cam = one.get("specificCameraInstructions"); 
                    if (cam instanceof String v) s.setSpecificCameraInstructions(trim60(v));
                    
                    Object audio = one.get("audioNotes"); 
                    if (audio instanceof String v) s.setAudioNotes(trim60(v));
                }
            }
            
            // Device orientation: derive from video metadata (just aspect ratio)
            String aspectRatio = deriveAspectRatioFromFirstScene(scenes);
            if (aspectRatio != null) {
                for (com.example.demo.model.Scene s : scenes) {
                    s.setDeviceOrientation(aspectRatio);  // e.g., "9:16" or "16:9"
                }
                log.info("Set device orientation for all scenes: {}", aspectRatio);
            }
            
            log.info("AI metadata generation completed successfully");
            
        } catch (Exception e) {
            log.error("Failed to generate AI metadata: {}", e.getMessage(), e);
            // Still derive and set device orientation even if AI guidance failed
            try {
                String aspectRatio = deriveAspectRatioFromFirstScene(scenes);
                if (aspectRatio != null) {
                    for (com.example.demo.model.Scene s : scenes) {
                        s.setDeviceOrientation(aspectRatio);  // e.g., "9:16" or "16:9"
                    }
                }
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Derive aspect ratio from first scene's keyframe (not preset!)
     * Returns "9:16" for portrait or "16:9" for landscape
     */
    private String deriveAspectRatioFromFirstScene(List<com.example.demo.model.Scene> scenes) {
        try {
            for (com.example.demo.model.Scene s : scenes) {
                // Use keyframe URL if available (faster and more reliable)
                if (s.getKeyframeUrl() != null && !s.getKeyframeUrl().isEmpty()) {
                    log.info("Deriving aspect ratio from scene {} keyframe", s.getSceneNumber());
                    
                    try {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                            new java.net.URL(s.getKeyframeUrl())
                        );
                        
                        if (img != null) {
                            int w = img.getWidth();
                            int h = img.getHeight();
                            boolean portrait = h >= w;
                            String aspectRatio = portrait ? "9:16" : "16:9";
                            
                            log.info("✅ Derived aspect ratio: {} from keyframe {}x{}", 
                                     aspectRatio, w, h);
                            return aspectRatio;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read keyframe for scene {}: {}", s.getSceneNumber(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to derive aspect ratio: {}", e.getMessage(), e);
        }
        
        log.warn("Could not derive aspect ratio, returning null");
        return null;
    }
    
    private String trim40(String s) {
        if (s == null) return null;
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
    
    private String trim60(String s) {
        if (s == null) return null;
        return s.length() > 60 ? s.substring(0, 60) : s;
    }
    
    // ==================== Template Assignment APIs ====================
    
    /**
     * Push template to groups with time-limited assignment
     * POST /content-manager/templates/{templateId}/push
     */
    @PostMapping("/{templateId}/push")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pushTemplateToGroups(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        // Get master template
        ManualTemplate master = templateDao.getTemplate(templateId);
        if (master == null) {
            throw new NoSuchElementException("Template not found with ID: " + templateId);
        }
        
        // Parse request
        @SuppressWarnings("unchecked")
        List<String> groupIds = (List<String>) requestBody.get("groupIds");
        Integer durationDays = (Integer) requestBody.get("durationDays");
        String pushedBy = (String) requestBody.get("pushedBy");
        
        if (groupIds == null || groupIds.isEmpty()) {
            throw new IllegalArgumentException("groupIds list is required");
        }
        
        // Create assignments for each group
        List<Map<String, Object>> results = new ArrayList<>();
        for (String groupId : groupIds) {
            com.example.demo.model.TemplateAssignment assignment = new com.example.demo.model.TemplateAssignment();
            assignment.setMasterTemplateId(templateId);
            assignment.setGroupId(groupId);
            assignment.setTemplateSnapshot(master);  // Deep copy
            assignment.setPushedBy(pushedBy);
            assignment.setDurationDays(durationDays);
            
            // Calculate expiration
            if (durationDays != null && durationDays > 0) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, durationDays);
                assignment.setExpiresAt(cal.getTime());
            }
            // If durationDays is null or 0, expiresAt stays null (permanent)
            
            String assignmentId = templateAssignmentDao.createAssignment(assignment);
            
            Map<String, Object> result = new HashMap<>();
            result.put("groupId", groupId);
            result.put("assignmentId", assignmentId);
            result.put("expiresAt", assignment.getExpiresAt());
            result.put("durationDays", durationDays);
            results.add(result);
        }
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("templateId", templateId);
        responseData.put("assignments", results);
        
        String message = i18nService.getMessage("template.pushed", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }
    
    /**
     * Get all assignments for a template
     * GET /content-manager/templates/{templateId}/assignments
     */
    @GetMapping("/{templateId}/assignments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTemplateAssignments(
            @PathVariable String templateId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        List<com.example.demo.model.TemplateAssignment> assignments = 
            templateAssignmentDao.getAssignmentsByTemplate(templateId);
        
        // Enrich with group information
        List<Map<String, Object>> enrichedAssignments = new ArrayList<>();
        for (com.example.demo.model.TemplateAssignment assignment : assignments) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", assignment.getId());
            data.put("groupId", assignment.getGroupId());
            data.put("pushedAt", assignment.getPushedAt());
            data.put("expiresAt", assignment.getExpiresAt());
            data.put("durationDays", assignment.getDurationDays());
            data.put("daysUntilExpiry", assignment.getDaysUntilExpiry());
            
            // Get group info
            try {
                com.example.demo.model.Group group = groupDao.findById(assignment.getGroupId());
                if (group != null) {
                    data.put("groupName", group.getGroupName());
                    data.put("memberCount", group.getMemberCount());
                }
            } catch (Exception e) {
                // Continue without group info
            }
            
            enrichedAssignments.add(data);
        }
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, enrichedAssignments));
    }
    
    /**
     * Renew an assignment (extend expiration)
     * POST /content-manager/assignments/{assignmentId}/renew
     */
    @PostMapping("/assignments/{assignmentId}/renew")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renewAssignment(
            @PathVariable String assignmentId,
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        com.example.demo.model.TemplateAssignment assignment = 
            templateAssignmentDao.getAssignment(assignmentId);
        
        if (assignment == null) {
            throw new NoSuchElementException("Assignment not found with ID: " + assignmentId);
        }
        
        Integer additionalDays = (Integer) requestBody.get("additionalDays");
        if (additionalDays == null || additionalDays <= 0) {
            throw new IllegalArgumentException("additionalDays must be positive");
        }
        
        // Extend expiration
        Calendar cal = Calendar.getInstance();
        if (assignment.getExpiresAt() != null) {
            cal.setTime(assignment.getExpiresAt());
        }
        cal.add(Calendar.DAY_OF_MONTH, additionalDays);
        
        assignment.setExpiresAt(cal.getTime());
        assignment.setLastRenewed(new Date());
        
        templateAssignmentDao.updateAssignment(assignment);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("assignmentId", assignmentId);
        responseData.put("newExpiresAt", assignment.getExpiresAt());
        responseData.put("daysUntilExpiry", assignment.getDaysUntilExpiry());
        
        String message = i18nService.getMessage("assignment.renewed", language);
        return ResponseEntity.ok(ApiResponse.ok(message, responseData));
    }
    
    /**
     * Delete an assignment
     * DELETE /content-manager/assignments/{assignmentId}
     */
    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        templateAssignmentDao.deleteAssignment(assignmentId);
        
        String message = i18nService.getMessage("assignment.deleted", language);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }
    
    /**
     * Move template to a folder
     * PUT /content-manager/templates/{templateId}/folder
     * Body: { "folderId": "folder_xxx" } or { "folderId": null } for root
     */
    @PutMapping("/{templateId}/folder")
    public ResponseEntity<ApiResponse<ManualTemplate>> moveTemplateToFolder(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        
        String folderId = (String) request.get("folderId");  // null = move to root
        
        ManualTemplate template = templateDao.getTemplate(templateId);
        if (template == null) {
            throw new NoSuchElementException("Template not found");
        }
        
        template.setFolderId(folderId);
        templateDao.updateTemplate(templateId, template);
        
        String message = i18nService.getMessage("template.updated", language);
        return ResponseEntity.ok(ApiResponse.ok(message, template));
    }
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContentManager.class);
}
// Change Log: Manual scenes always set sceneSource="manual" and overlayType="grid" for dual scene system
// Added manual-with-ai endpoint for creating templates with per-scene AI analysis (no scene detection)
// Added AI metadata generation using reasoning model (same as AI template)
// Added template assignment APIs for time-limited template pushing (Phase 2)
