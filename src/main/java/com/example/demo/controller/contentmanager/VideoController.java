package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.VideoDao;
import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import com.example.demo.service.TemplateSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.demo.ai.template.AITemplateGenerator;
import com.example.demo.ai.template.DefaultAITemplateGenerator;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-manager/videos")
public class VideoController {
    @Autowired
    private AITemplateGenerator aiTemplateGenerator;
    @Autowired
    private DefaultAITemplateGenerator defaultAITemplateGenerator;
    private boolean useDefaultAITemplateGenerator = false;
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
    
    private ManualTemplate generateAITemplate(Video video, String language) {
        // Use AI template generator to create template
        System.out.println("Generating AI template for video ID: " + video.getId() + " in language: " + language);
        if (useDefaultAITemplateGenerator) {
            return defaultAITemplateGenerator.generateTemplate(video, language);
        }
        return aiTemplateGenerator.generateTemplate(video, language);
    }
    
    @Autowired
    private VideoDao videoDao;

    @Autowired
    private TemplateDao templateDao; 

    @Autowired(required = false)
    private com.example.demo.service.FirebaseStorageService firebaseStorageService;

    @Autowired(required = false)
    private com.google.cloud.firestore.Firestore db;

    @Autowired
    private TemplateSubscriptionService templateSubscriptionService;

    @PostMapping("/{videoId}/approve")
    public ResponseEntity<String> approveVideo(@PathVariable String videoId) throws Exception {
        com.google.cloud.firestore.DocumentReference videoRef = db.collection("submittedVideos").document(videoId);
        com.google.cloud.firestore.DocumentSnapshot videoSnap = videoRef.get().get();
        if (!videoSnap.exists()) return ResponseEntity.notFound().build();
        String creatorId = (String) videoSnap.get("uploadedBy");
        videoRef.update("publishStatus", "approved");
        // Send notification to creator
        com.google.cloud.firestore.DocumentReference userRef = db.collection("users").document(creatorId);
        String notifId = java.util.UUID.randomUUID().toString();
        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("type", "video_approved");
        notif.put("message", "Your video was approved by the manager.");
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read", false);
        userRef.update("notifications." + notifId, notif);
        return ResponseEntity.ok("Video approved and creator notified.");
    }

    @PostMapping("/{videoId}/reject")
    public ResponseEntity<String> rejectVideo(@PathVariable String videoId, @RequestBody java.util.Map<String, String> body) throws Exception {
        com.google.cloud.firestore.DocumentReference videoRef = db.collection("submittedVideos").document(videoId);
        com.google.cloud.firestore.DocumentSnapshot videoSnap = videoRef.get().get();
        if (!videoSnap.exists()) return ResponseEntity.notFound().build();
        String creatorId = (String) videoSnap.get("uploadedBy");
        String reason = body.getOrDefault("reason", "No reason provided");
        String suggestion = body.getOrDefault("suggestion", "");
        videoRef.update("publishStatus", "rejected", "reason", reason, "suggestion", suggestion);
        // Send notification to creator
        com.google.cloud.firestore.DocumentReference userRef = db.collection("users").document(creatorId);
        String notifId = java.util.UUID.randomUUID().toString();
        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("type", "video_rejected");
        notif.put("message", "Your video was rejected. Reason: " + reason + (suggestion.isEmpty() ? "" : ". Suggestion: " + suggestion));
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read", false);
        userRef.update("notifications." + notifId, notif);
        return ResponseEntity.ok("Video rejected and creator notified.");
    }

    @PostMapping("/{videoId}/publish")
    public ResponseEntity<String> publishVideo(@PathVariable String videoId, @RequestParam String publisherId) throws Exception {
        com.google.cloud.firestore.DocumentReference videoRef = db.collection("submittedVideos").document(videoId);
        com.google.cloud.firestore.DocumentSnapshot videoSnap = videoRef.get().get();
        if (!videoSnap.exists()) return ResponseEntity.notFound().build();
        
        String currentStatus = (String) videoSnap.get("publishStatus");
        if (!"approved".equals(currentStatus)) {
            return ResponseEntity.badRequest().body("Can only publish approved videos");
        }
        
        String creatorId = (String) videoSnap.get("uploadedBy");
        videoRef.update("publishStatus", "published", "publishedAt", com.google.cloud.firestore.FieldValue.serverTimestamp(), "publishedBy", publisherId);
        
        // Send notification to creator
        com.google.cloud.firestore.DocumentReference userRef = db.collection("users").document(creatorId);
        String notifId = java.util.UUID.randomUUID().toString();
        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("type", "video_published");
        notif.put("message", "Your video has been published!");
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read", false);
        userRef.update("notifications." + notifId, notif);
        return ResponseEntity.ok("Video published and creator notified.");
    }

    @PostMapping("/upload")
    public ResponseEntity<Video> uploadVideo(@RequestParam("file") MultipartFile file,
                                             @RequestParam("userId") String userId,
                                             @RequestParam(value = "title", required = false) String title,
                                             @RequestParam(value = "description", required = false) String description,
                                             @RequestParam(value = "templateId", required = false) String templateId,
                                             @RequestParam(value = "groupIds", required = false) String groupIdsStr,
                                             @RequestHeader(value = "Accept-Language", required = false, defaultValue = "en") String acceptLanguage) {
        try {
            // Upload to Firebase Storage and extract thumbnail
            // Generate videoId first
            String videoId = java.util.UUID.randomUUID().toString();
            com.example.demo.service.FirebaseStorageService.UploadResult result = firebaseStorageService.uploadVideoWithThumbnail(file, userId, videoId);

            // Create video
            Video video = new Video();
            video.setId(videoId);
            video.setUserId(userId);
            video.setTitle(title != null ? title : "");
            video.setDescription(description != null ? description : "");
            video.setUrl(result.videoUrl);
            video.setThumbnailUrl(result.thumbnailUrl);
            Video savedVideo = videoDao.saveVideo(video);
            // Create a default template if no templateId is provided
            // Determine template creation strategy
            try {
                if (templateId == null) {
                    // No templateId provided, generate a new template using AI and associate it to the video
                    System.out.printf("No templateId provided, generating AI template for video ID: %s\n", savedVideo.getId());
                    // Detect language from header
                    String language = detectLanguage(acceptLanguage);
                    
                    ManualTemplate aiGeneratedTemplate = generateAITemplate(savedVideo, language);
                    aiGeneratedTemplate.setUserId(userId);
                    aiGeneratedTemplate.setVideoId(savedVideo.getId());
                    aiGeneratedTemplate.setTemplateTitle(title != null ? title : "AI Generated Template");
                    String savedTemplateId = templateDao.createTemplate(aiGeneratedTemplate);
                    savedVideo.setTemplateId(savedTemplateId);
                    videoDao.updateVideo(savedVideo);
                    
                    // Handle group subscription for AI-generated template
                    if (groupIdsStr != null && !groupIdsStr.trim().isEmpty()) {
                        List<String> groupIds = java.util.Arrays.asList(groupIdsStr.split(","));
                        TemplateSubscriptionService.SubscriptionResult subscriptionResult = 
                            templateSubscriptionService.batchSubscribeToTemplate(savedTemplateId, groupIds);
                        
                        System.out.printf("AI-generated template %s subscribed to %d users across %d groups%n", 
                            savedTemplateId, subscriptionResult.getTotalUsersAffected(), subscriptionResult.getProcessedGroups().size());
                    }
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
            } catch (Exception e) {
                System.err.println("Error creating template: " + e.getMessage());
                throw new RuntimeException("Failed to create or update template", e);
            }

            // Log
            System.out.println("[INFO] [uploadVideo] Uploaded video and thumbnail for user " + userId + ", videoId " + savedVideo.getId());

            return ResponseEntity.ok(savedVideo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Video>> getVideosByUserId(@PathVariable String userId) {
        try {
            List<Video> videos = videoDao.getVideosByUserId(userId);
            return ResponseEntity.ok(videos);
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<Video> getVideoById(@PathVariable String videoId) {
        try {
            Video video = videoDao.getVideoById(videoId);
            if (video != null) {
                return ResponseEntity.ok(video);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
