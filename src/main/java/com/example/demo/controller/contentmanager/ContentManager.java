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
    




    @Autowired
    private com.example.demo.service.TemplateCascadeDeletionService templateCascadeDeletionService;
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private com.example.demo.dao.GroupDao groupDao;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    @Autowired
    private com.example.demo.ai.services.UnifiedSceneAnalysisService unifiedSceneAnalysisService;
    
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
        
        // Optimize: Query only relevant submissions using whereIn (Firestore limit: 10 items per whereIn)
        // If more than 10 assignmentIds, we need to batch the queries
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> published = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        
        if (assignmentIds.isEmpty()) {
            // No assignments, return empty result
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            result.put("pending", pending);
            result.put("approved", approved);
            result.put("published", published);
            result.put("rejected", rejected);
            return ResponseEntity.ok(ApiResponse.ok(i18nService.getMessage("operation.success", language), result));
        }
        
        // Firestore whereIn limit is 10, so batch if needed
        List<String> assignmentIdList = new ArrayList<>(assignmentIds);
        int batchSize = 10;
        
        for (int i = 0; i < assignmentIdList.size(); i += batchSize) {
            List<String> batch = assignmentIdList.subList(i, Math.min(i + batchSize, assignmentIdList.size()));
            
            // Query with filter - MUCH faster than getting all submissions!
            com.google.cloud.firestore.Query query = db.collection("submittedVideos")
                .whereIn("assignmentId", batch);
            
            com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> querySnapshot = query.get();
            
            for (com.google.cloud.firestore.DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
                Map<String, Object> data = doc.getData();
                if (data == null) continue;
                
                String uploadedBy = (String) data.get("uploadedBy");
                
                // Filter by member (user must be in one of manager's groups)
                if (!memberIds.contains(uploadedBy)) continue;
                
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
                String assignmentId = (String) data.get("assignmentId");
                String templateId = (String) data.get("templateId");
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
        }
        
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("published", published);
        result.put("rejected", rejected);
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, result));
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
        log.info("=== UPDATE TEMPLATE REQUEST ===");
        log.info("Template ID: {}", templateId);
        log.info("Template Title: {}", updatedTemplate.getTemplateTitle());
        log.info("Total Video Length (incoming): {}", updatedTemplate.getTotalVideoLength());
        log.info("Number of scenes: {}", updatedTemplate.getScenes() != null ? updatedTemplate.getScenes().size() : 0);
        
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        updatedTemplate.setId(templateId); // Ensure ID matches path parameter
        
        // Mark all scenes as manual with grid overlay for updates and validate minimum duration
        if (updatedTemplate.getScenes() != null) {
            log.info("Processing {} scenes for update", updatedTemplate.getScenes().size());
            int sceneIndex = 0;
            for (com.example.demo.model.Scene scene : updatedTemplate.getScenes()) {
                sceneIndex++;
                log.info("Scene {}: title='{}', duration={}s", 
                    sceneIndex, scene.getSceneTitle(), scene.getSceneDurationInSeconds());
                
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
            log.info("✅ Template updated successfully");
            String message = i18nService.getMessage("template.updated", language);
            return ResponseEntity.ok(ApiResponse.ok(message, updatedTemplate));
        } else {
            log.error("❌ Template not found with ID: {}", templateId);
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
        
        // Get user's group to check auto-reject threshold
        // Note: This endpoint is only called by managers viewing their submissions
        // So we don't need managerId param - we infer it from the submission
        String uploadedBy = (String) videoData.get("uploadedBy");
        double autoRejectThreshold = 0.0;  // Default: no filtering
        
        if (uploadedBy != null) {
            try {
                // Get assignment to find the manager, then get their groups
                String assignmentId = (String) videoData.get("assignmentId");
                if (assignmentId != null) {
                    com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
                    if (assignment != null) {
                        // Get the group for this assignment
                        com.example.demo.model.Group group = groupDao.findById(assignment.getGroupId());
                        if (group != null) {
                            autoRejectThreshold = group.getAiAutoRejectThreshold();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get group threshold for filtering: {}", e.getMessage());
            }
        }
        
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
                        // Filter: Skip scenes below auto-reject threshold
                        Double similarityScore = sceneSubmission.getSimilarityScore();
                        if (similarityScore != null && similarityScore >= 0 && similarityScore < autoRejectThreshold) {
                            log.debug("Filtering scene {} - similarity {} below threshold {}", 
                                sceneId, similarityScore, autoRejectThreshold);
                            continue;  // Skip this scene
                        }
                        
                        Map<String, Object> fullSceneData = new HashMap<>();
                        fullSceneData.put("sceneId", sceneSubmission.getId());
                        fullSceneData.put("sceneNumber", sceneSubmission.getSceneNumber());
                        fullSceneData.put("sceneTitle", sceneSubmission.getSceneTitle());
                        fullSceneData.put("videoUrl", sceneSubmission.getVideoUrl());
                        // Attach a short-lived signed URL for preview/streaming in manager UI
                        try {
                            String signed = sceneSubmissionDao.getSignedUrl(sceneSubmission.getVideoUrl());
                            fullSceneData.put("videoSignedUrl", signed);
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
            if (compiledUrl instanceof String) {
                String signed = sceneSubmissionDao.getSignedUrl((String) compiledUrl);
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
    
    @Autowired(required = false)
    private com.example.demo.ai.subtitle.ASRSubtitleExtractor asrSubtitleExtractor;
    
    @Autowired
    private com.example.demo.service.AlibabaOssStorageService alibabaOssStorageService;
    
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
        log.info("Speech extraction: ASR per scene video (Alibaba Cloud Qwen)");
        
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
            
            // 1. Get video duration using FFmpeg (MUST BE BEFORE UPLOAD!)
            log.info("🎬 Extracting duration for scene {} video file: {}", metadata.getSceneNumber(), videoFile.getOriginalFilename());
            long videoDurationSeconds = 0;
            try {
                // Save video to temp file for duration extraction (copy bytes, don't use transferTo)
                java.io.File tempVideo = java.io.File.createTempFile("duration-", ".mp4");
                log.info("📁 Created temp file: {}", tempVideo.getAbsolutePath());
                
                // Copy bytes from multipart file to temp file
                try (java.io.InputStream is = videoFile.getInputStream();
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(tempVideo)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                log.info("✅ Video file copied to temp location, size: {} bytes", tempVideo.length());
                
                // Use FFprobe to get duration
                ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", tempVideo.getAbsolutePath()
                );
                log.info("🔧 Running FFprobe command...");
                Process proc = pb.start();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream())
                );
                String durationStr = reader.readLine();
                int exitCode = proc.waitFor();
                tempVideo.delete();
                
                log.info("FFprobe exit code: {}, output: '{}'", exitCode, durationStr);
                
                if (durationStr != null && !durationStr.isEmpty()) {
                    videoDurationSeconds = (long) Double.parseDouble(durationStr);
                    log.info("✅ SUCCESS: Video duration for scene {}: {} seconds", metadata.getSceneNumber(), videoDurationSeconds);
                } else {
                    log.error("❌ FAILED: FFprobe returned empty duration for scene {}", metadata.getSceneNumber());
                }
            } catch (Exception e) {
                log.error("❌ EXCEPTION: Failed to extract video duration for scene {}: {} - {}", 
                    metadata.getSceneNumber(), e.getClass().getSimpleName(), e.getMessage());
                e.printStackTrace();
                // Continue without duration - will default to 0
            }
            
            // 2. Upload video to OSS (AFTER duration extraction)
            String videoId = java.util.UUID.randomUUID().toString();
            com.example.demo.model.Video video = videoDao.uploadAndSaveVideo(videoFile, userId, videoId);
            log.info("✅ Video uploaded to OSS: {}", video.getUrl());
            
            // 3. Update video with duration
            log.info("💾 Updating Video object with duration: {} seconds", videoDurationSeconds);
            video.setDurationSeconds(videoDurationSeconds);
            videoDao.updateVideo(video);
            log.info("✅ Video saved with ID: {}, duration: {} seconds", videoId, videoDurationSeconds);
            
            // 4. Extract speech transcript using ASR (per scene video)
            String scriptLine = "";
            if (asrSubtitleExtractor != null) {
                try {
                    log.info("🎤 Extracting speech transcript for scene {} using ASR", metadata.getSceneNumber());
                    
                    // Generate signed URL with 1 hour expiration for ASR processing
                    String signedUrl = alibabaOssStorageService.generateSignedUrl(video.getUrl(), 60, java.util.concurrent.TimeUnit.MINUTES);
                    log.info("✅ Generated signed URL for ASR (expires in 1 hour)");
                    
                    List<com.example.demo.ai.subtitle.SubtitleSegment> transcript = 
                        asrSubtitleExtractor.extract(signedUrl, language);
                    
                    if (transcript != null && !transcript.isEmpty()) {
                        // Concatenate all transcript segments into scriptLine
                        scriptLine = transcript.stream()
                            .map(com.example.demo.ai.subtitle.SubtitleSegment::getText)
                            .collect(java.util.stream.Collectors.joining(" "));
                        log.info("✅ ASR extracted {} segments, total text length: {} chars", 
                                 transcript.size(), scriptLine.length());
                        log.info("📝 Scene {} script: {}", metadata.getSceneNumber(), 
                                 scriptLine.length() > 100 ? scriptLine.substring(0, 100) + "..." : scriptLine);
                    } else {
                        log.warn("⚠️ ASR returned empty transcript for scene {}", metadata.getSceneNumber());
                    }
                } catch (Exception e) {
                    log.warn("⚠️ ASR extraction failed for scene {}: {} - {}", 
                             metadata.getSceneNumber(), e.getClass().getSimpleName(), e.getMessage());
                    // Continue without transcript
                }
            } else {
                log.warn("⚠️ ASR service not available, scene {} will not have script line", metadata.getSceneNumber());
            }
            
            // 5. Analyze as single scene using UnifiedSceneAnalysisService
            log.info("🎬 Creating Scene object for scene {}", metadata.getSceneNumber());
            com.example.demo.model.Scene aiScene = new com.example.demo.model.Scene();
            aiScene.setSceneSource("manual");
            aiScene.setSceneNumber(metadata.getSceneNumber());
            aiScene.setSceneTitle(metadata.getSceneTitle());
            aiScene.setSceneDescription(metadata.getSceneDescription());
            aiScene.setVideoId(videoId);
            aiScene.setSceneDurationInSeconds(videoDurationSeconds); // Set duration from video metadata
            aiScene.setScriptLine(scriptLine); // Set ASR-extracted script
            log.info("✅ Scene {} duration set to: {} seconds", metadata.getSceneNumber(), videoDurationSeconds);
            
            // 6. Analyze the video for AI metadata (VL analysis, overlays, etc.)
            try {
                if (unifiedSceneAnalysisService == null) {
                    log.error("❌ UnifiedSceneAnalysisService is NULL - service not autowired!");
                } else {
                    log.info("✅ Calling UnifiedSceneAnalysisService for scene {} with videoUrl: {}", 
                        metadata.getSceneNumber(), 
                        video.getUrl() != null ? video.getUrl().substring(0, Math.min(80, video.getUrl().length())) : "null");
                    
                    com.example.demo.ai.services.SceneAnalysisResult analysisResult = 
                        unifiedSceneAnalysisService.analyzeScene(video.getUrl(), language, null, null);
                    
                    log.info("✅ UnifiedSceneAnalysisService returned result for scene {}", metadata.getSceneNumber());
                    
                    if (analysisResult == null) {
                        log.warn("⚠️ Analysis result is NULL for scene {}", metadata.getSceneNumber());
                    } else {
                        analysisResult.applyToScene(aiScene);
                        log.info("✅ Applied analysis result to scene {}", metadata.getSceneNumber());
                    }
                }
            } catch (Exception e) {
                log.error("❌ Scene analysis failed for scene {}: {} - {}", 
                    metadata.getSceneNumber(), e.getClass().getSimpleName(), e.getMessage(), e);
                // Continue with basic scene
            }
            
            // Fallback to grid if no overlay type set
            if (aiScene.getOverlayType() == null) {
                aiScene.setOverlayType("grid");
            }
            
            aiAnalyzedScenes.add(aiScene);
            log.info("Scene {} analyzed successfully with overlay type: {}", 
                     metadata.getSceneNumber(), aiScene.getOverlayType());
        }
        
        // 7. Create template with calculated metadata
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
        
        // 8. Generate AI metadata using reasoning model (sets deviceOrientation)
        log.info("Generating AI metadata for manual template with {} scenes", aiAnalyzedScenes.size());
        generateManualTemplateMetadata(template, aiAnalyzedScenes, language, templateDescription);
        
        // 9. Derive video format from first scene's device orientation (AFTER AI metadata)
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
        
        // 10. Save template (groups are now assigned via push button with TemplateAssignment)
        String templateId = templateDao.createTemplate(template);
        userDao.addCreatedTemplate(userId, templateId);
        
        log.info("Manual template created successfully: {} with {} scenes", templateId, aiAnalyzedScenes.size());
        
        // 11. Response
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
    
    // Removed getTemplateAssignments - unused endpoint (0 references)
    
    // Removed renewAssignment - unused endpoint (0 references)
    
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
