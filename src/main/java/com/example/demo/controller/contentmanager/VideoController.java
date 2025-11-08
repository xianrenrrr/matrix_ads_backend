package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.VideoDao;
import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;

import com.example.demo.service.I18nService;
import com.example.demo.api.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.demo.ai.services.TemplateAIService;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/content-manager/videos")
public class VideoController {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VideoController.class);
    
    @Autowired
    private TemplateAIService aiTemplateGenerator;
    private String detectLanguage(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isEmpty()) {
            return "en";
        }
        String[] languages = acceptLanguageHeader.split(",");
        for (String lang : languages) {
            String cleanLang = lang.trim().split(";")[0].toLowerCase();
            if (cleanLang.startsWith("zh")) {
                return "zh";
            }
        }
        return "en";
    }
    
    private ManualTemplate generateAITemplate(Video video, String language, String userDescription) {
        // Use AI template generator to create template with user description
        System.out.println("Generating AI template for video ID: " + video.getId() + " in language: " + language + 
                          " with user description: " + (userDescription != null ? "provided" : "none"));
        return aiTemplateGenerator.generateTemplate(video, language, userDescription);
    }
    
    @Autowired
    private VideoDao videoDao;

    @Autowired
    private TemplateDao templateDao; 

    @Autowired
    private I18nService i18nService;


    @Autowired(required = false)
    private com.google.cloud.firestore.Firestore db;
    
    @Autowired
    private com.example.demo.dao.UserDao userDao;
    


    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Video>> getVideo(@PathVariable String videoId) {
        try {
            Video video = videoDao.getVideoById(videoId);
            if (video == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.ok("Video found", video));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.fail("Failed to get video: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{videoId}/stream")
    public ResponseEntity<ApiResponse<String>> streamVideo(@PathVariable String videoId) {
        try {
            Video video = videoDao.getVideoById(videoId);
            if (video == null) {
                return ResponseEntity.status(404).body(ApiResponse.fail("Video not found"));
            }
            
            // DAO handles signed URL generation
            String signedUrl = videoDao.getSignedUrl(video.getUrl());
            return ResponseEntity.ok(ApiResponse.ok("Signed URL generated", signedUrl));
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.fail("Failed to generate video URL"));
        }
    }

    // REMOVED: /{videoId}/approve and /{videoId}/reject endpoints
    // These bypassed the scene-by-scene review workflow
    // Use scene manual override instead: /content-manager/scenes/{sceneId}/manual-override

    @Autowired
    private com.example.demo.dao.CompiledVideoDao compiledVideoDao;
    
    @Autowired
    private com.example.demo.service.VideoCompilationService videoCompilationService;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    @PostMapping("/{videoId}/publish")
    public ResponseEntity<ApiResponse<String>> publishVideo(
            @PathVariable String videoId, 
            @RequestParam String publisherId,
            @RequestParam(required = false, defaultValue = "white") String subtitleColor,
            @RequestParam(required = false, defaultValue = "24") Integer subtitleSize,
            @RequestParam(required = false, defaultValue = "center") String subtitlePosition,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        com.google.cloud.firestore.DocumentReference videoRef = db.collection("submittedVideos").document(videoId);
        com.google.cloud.firestore.DocumentSnapshot videoSnap = videoRef.get().get();
        if (!videoSnap.exists()) {
            throw new NoSuchElementException("Video not found with ID: " + videoId);
        }
        
        String currentStatus = (String) videoSnap.get("publishStatus");
        if (!"approved".equals(currentStatus)) {
            throw new IllegalArgumentException("Can only publish approved videos");
        }
        
        String creatorId = (String) videoSnap.get("uploadedBy");
        String assignmentId = (String) videoSnap.get("assignmentId");
        
        if (assignmentId == null || assignmentId.isEmpty()) {
            throw new IllegalArgumentException("Assignment ID not found in submitted video");
        }
        
        // Get the template from assignment for compilation
        com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(assignmentId);
        if (assignment == null) {
            throw new IllegalArgumentException("Assignment not found: " + assignmentId);
        }
        com.example.demo.model.ManualTemplate template = assignment.getTemplateSnapshot();
        if (template == null) {
            throw new IllegalArgumentException("Template snapshot not found in assignment: " + assignmentId);
        }
        
        // COMPILE VIDEO NOW - manager clicked publish (with customized subtitles!)
        com.example.demo.service.SubtitleBurningService.SubtitleOptions subtitleOptions = 
            new com.example.demo.service.SubtitleBurningService.SubtitleOptions();
        
        // Apply custom subtitle styling
        subtitleOptions.textColor = subtitleColor;
        subtitleOptions.fontSize = subtitleSize;
        
        // Map position to alignment (always centered horizontally)
        // Top: 8, Middle: 5, Bottom: 2
        if ("top".equalsIgnoreCase(subtitlePosition)) {
            subtitleOptions.alignment = 8;  // Top center
        } else if ("middle".equalsIgnoreCase(subtitlePosition) || "center".equalsIgnoreCase(subtitlePosition)) {
            subtitleOptions.alignment = 5;  // Middle center
        } else if ("bottom".equalsIgnoreCase(subtitlePosition)) {
            subtitleOptions.alignment = 2;  // Bottom center
        } else {
            subtitleOptions.alignment = 2;  // Default: bottom center
        }
        
        log.info("Publishing video with subtitle options: color={}, size={}, position={} (alignment={})", 
                 subtitleColor, subtitleSize, subtitlePosition, subtitleOptions.alignment);
        
        // Log template scenes for debugging
        if (template.getScenes() != null) {
            log.info("📋 Template has {} scenes for subtitle generation", template.getScenes().size());
            for (int i = 0; i < template.getScenes().size(); i++) {
                com.example.demo.model.Scene scene = template.getScenes().get(i);
                int segmentCount = (scene.getSubtitleSegments() != null) ? scene.getSubtitleSegments().size() : 0;
                log.info("  Scene {}: {} subtitle segments", i+1, segmentCount);
                if (segmentCount > 0 && scene.getSubtitleSegments() != null) {
                    for (com.example.demo.ai.subtitle.SubtitleSegment seg : scene.getSubtitleSegments()) {
                        log.info("    - \"{}\" ({}ms - {}ms)", seg.getText(), seg.getStartTimeMs(), seg.getEndTimeMs());
                    }
                }
            }
        } else {
            log.warn("⚠️ Template has NO scenes!");
        }
        
        // Use assignmentId (not templateId) because submittedVideos uses assignmentId in composite key
        String compiledVideoUrl = videoCompilationService.compileVideoWithSubtitles(
            assignmentId, creatorId, publisherId, subtitleOptions);
        com.example.demo.model.CompiledVideo compiledVideo = new com.example.demo.model.CompiledVideo(assignmentId, creatorId, publisherId);
        compiledVideo.setVideoUrl(compiledVideoUrl);
        compiledVideo.setStatus("published");
        compiledVideoDao.save(compiledVideo);
        
        // Update status to published
        videoRef.update("publishStatus", "published", 
                       "publishedAt", com.google.cloud.firestore.FieldValue.serverTimestamp(), 
                       "publishedBy", publisherId,
                       "compiledVideoUrl", compiledVideoUrl);
        
        // Send notification to creator
        com.google.cloud.firestore.DocumentReference userRef = db.collection("users").document(creatorId);
        String notifId = java.util.UUID.randomUUID().toString();
        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("type", "video_published");
        notif.put("message", "Your video has been published!");
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read", false);
        userRef.update("notifications." + notifId, notif);
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, "Video compiled and published successfully."));
    }

    @PostMapping("/upload")
    public ResponseEntity<Video> uploadVideo(@RequestParam("file") MultipartFile file,
                                             @RequestParam("userId") String userId,
                                             @RequestParam(value = "title", required = false) String title,
                                             @RequestParam(value = "description", required = false) String description,
                                             @RequestParam(value = "templateId", required = false) String templateId,
                                             @RequestParam(value = "groupIds", required = false) String groupIdsStr,
                                             @RequestParam(value = "folderId", required = false) String folderId,
                                             @RequestHeader(value = "Accept-Language", required = false, defaultValue = "en") String acceptLanguage) throws Exception {
        System.out.println("=== VIDEO UPLOAD REQUEST ===");
        System.out.println("Accept-Language header: " + acceptLanguage);
        System.out.println("User ID: " + userId);
        System.out.println("Title: " + title);
        System.out.println("Description: " + description);
        System.out.println("Template ID: " + templateId);
        System.out.println("Group IDs: " + groupIdsStr);
        System.out.println("Folder ID: " + folderId);
        System.out.println("=============================");
        
        // DAO handles upload and save
        String videoId = java.util.UUID.randomUUID().toString();
        Video savedVideo = videoDao.uploadAndSaveVideo(file, userId, videoId);
        savedVideo.setTitle(title != null ? title : "");
        savedVideo.setDescription(description != null ? description : "");
        videoDao.updateVideo(savedVideo);
        // Create a default template if no templateId is provided
        // Determine template creation strategy
        if (templateId == null) {
            // No templateId provided, generate a new template using AI and associate it to the video
            System.out.printf("No templateId provided, generating AI template for video ID: %s\n", savedVideo.getId());
            // Detect language from header
            String language = detectLanguage(acceptLanguage);
            System.out.println("Detected language: " + language);
            
            ManualTemplate aiGeneratedTemplate = generateAITemplate(savedVideo, language, description);
            aiGeneratedTemplate.setUserId(userId);  // Always set to actual creator
            aiGeneratedTemplate.setVideoId(savedVideo.getId());
            aiGeneratedTemplate.setThumbnailUrl(savedVideo.getThumbnailUrl());  // Set thumbnail from video
            aiGeneratedTemplate.setCreatedAt(new java.util.Date());  // Set creation timestamp for permission checks
            
            // Set folderId if provided
            if (folderId != null && !folderId.isBlank()) {
                aiGeneratedTemplate.setFolderId(folderId);
                System.out.println("Setting folderId: " + folderId + " for AI template");
            }
            
            // Preserve AI-generated title; only prepend user title if provided
            if (title != null && !title.isBlank()) {
                String today = java.time.LocalDate.now().toString();
                aiGeneratedTemplate.setTemplateTitle(title + " - AI 模版 " + today);
            }
            
            // Create template (groups are now assigned via push button with TemplateAssignment)
            String savedTemplateId = templateDao.createTemplate(aiGeneratedTemplate);
            
            // Determine who should own the template in created_Templates
            try {
                String templateOwnerId = userId;
                com.example.demo.model.User user = userDao.findById(userId);
                if (user != null && "employee".equals(user.getRole()) && user.getCreatedBy() != null) {
                    // If creator is an employee, add to manager's created_Templates only
                    templateOwnerId = user.getCreatedBy();
                    System.out.println("Employee " + userId + " template " + savedTemplateId + " added to manager " + templateOwnerId);
                }
                // Add template to manager's created_Templates (or creator's if they are a manager)
                userDao.addCreatedTemplate(templateOwnerId, savedTemplateId);
            } catch (Exception e) {
                System.err.println("Failed to add template to user: " + e.getMessage());
                // Continue - template is still created successfully
            }
            
            savedVideo.setTemplateId(savedTemplateId);
            videoDao.updateVideo(savedVideo);
            
            System.out.printf("AI-generated template %s created%n", savedTemplateId);
        } else {
            // If templateId is provided, link the existing template
            savedVideo.setTemplateId(templateId);
            videoDao.updateVideo(savedVideo);

            // Update the template with video ID
            ManualTemplate existingTemplate = templateDao.getTemplate(templateId);
            if (existingTemplate != null) {
                existingTemplate.setVideoId(savedVideo.getId());
                templateDao.updateTemplate(templateId, existingTemplate);
            }
            // No need to update user or template subcollections; created_template is managed in ContentManager.
        }

        // Log
        System.out.println("[INFO] [uploadVideo] Uploaded video and thumbnail for user " + userId + ", videoId " + savedVideo.getId());
        
        System.out.println("=== VIDEO UPLOAD SUCCESS ===");
        System.out.println("Video ID: " + savedVideo.getId());
        System.out.println("Template ID: " + savedVideo.getTemplateId());
        System.out.println("Title: " + savedVideo.getTitle());
        System.out.println("URL: " + savedVideo.getUrl());
        System.out.println("Returning status: 200 OK");
        System.out.println("=============================");

        return ResponseEntity.ok(savedVideo);
    }
}
