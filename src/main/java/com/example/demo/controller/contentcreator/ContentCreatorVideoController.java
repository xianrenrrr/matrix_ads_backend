package com.example.demo.controller.contentcreator;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.StorageClient;
import com.google.firebase.FirebaseApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;

import com.example.demo.dao.VideoDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import com.example.demo.ai.EditSuggestionService;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-creator/videos")
public class ContentCreatorVideoController {
    @Autowired
    private Firestore db;
    
    @Autowired
    private VideoDao videoDao;
    
    @Autowired(required = false)
    private TemplateDao templateDao;
    
    @Autowired
    private EditSuggestionService editSuggestionService;
    
    @Autowired(required = false)
    private FirebaseApp firebaseApp;
    
    @Autowired
    private I18nService i18nService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadContentCreatorVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") String templateId,
            @RequestParam("userId") String userId,
            @RequestHeader(value = "Accept-Language", required = false, defaultValue = "en") String acceptLanguage
    ) throws ExecutionException, InterruptedException {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        if (file == null || file.isEmpty() || !StringUtils.hasText(templateId) || !StringUtils.hasText(userId)) {
            String message = i18nService.getMessage("bad.request", language);
            return ResponseEntity.badRequest().body(ApiResponse.fail(message, "Missing required parameters"));
        }
        try {
            // Check if Firebase is available
            if (firebaseApp == null) {
                String message = i18nService.getMessage("server.error", language);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.fail(message, "Firebase Storage is not available. Please check configuration."));
            }
            
            // Upload new video to GCS
            String newVideoId = UUID.randomUUID().toString();
            String newObjectName = String.format("content-creator-videos/%s/%s/%s.mp4", userId, templateId, newVideoId);
            StorageClient.getInstance(firebaseApp).bucket().create(newObjectName, file.getInputStream(), file.getContentType());
            String videoUrl = String.format("https://storage.googleapis.com/%s/%s", StorageClient.getInstance(firebaseApp).bucket().getName(), newObjectName);

            // Perform similarity analysis immediately after upload
            double similarityScore = 0.0;
            List<String> suggestions = new ArrayList<>();
            
            try {
                // Get template and example video for comparison
                if (templateDao != null) {
                    ManualTemplate template = templateDao.getTemplate(templateId);
                    if (template != null && template.getVideoId() != null) {
                        Video exampleVideo = videoDao.getVideoById(template.getVideoId());
                        if (exampleVideo != null) {
                            System.out.println("Running similarity analysis for uploaded video");
                            System.out.println("Comparing with example video: " + exampleVideo.getUrl());
                            
                            // Create a temporary video object for the submitted video
                            Video submittedVideo = new Video();
                            submittedVideo.setId(newVideoId);
                            submittedVideo.setUrl(videoUrl);
                            submittedVideo.setTemplateId(templateId);
                            
                            // TODO: Replace empty lists with real scene extraction from videos
                            // Use VideoComparisonIntegrationService.getUserVideoScenesById() to get actual scene data
                            List<Map<String, String>> userScenes = new ArrayList<>();
                            List<Map<String, String>> exampleScenes = new ArrayList<>();
                            
                            if (userScenes != null && !userScenes.isEmpty() && exampleScenes != null && !exampleScenes.isEmpty()) {
                                // Calculate similarity
                                similarityScore = calculateSimilarity(userScenes, exampleScenes);
                                
                                // Language already detected above
                                
                                // Generate suggestions
                                suggestions = generateSuggestions(exampleScenes.get(0), userScenes.get(0), similarityScore, language);
                                
                                System.out.println("Similarity analysis complete: " + similarityScore);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error during similarity analysis: " + e.getMessage());
                // Continue with upload even if analysis fails
            }

            // Store metadata in submittedVideos/{videoId}
            Map<String, Object> feedback = new HashMap<>();
            feedback.put("similarityScore", similarityScore);
            feedback.put("suggestions", suggestions);
            feedback.put("publishStatus", "pending");
            feedback.put("requestedApproval", true);
            
            Map<String, Object> videoMeta = new HashMap<>();
            videoMeta.put("videoId", newVideoId);
            videoMeta.put("templateId", templateId);
            videoMeta.put("uploadedBy", userId);
            videoMeta.put("videoUrl", videoUrl);
            videoMeta.put("thumbnailUrl", null); // Add thumbnail logic if needed
            videoMeta.put("feedback", feedback);
            videoMeta.put("submittedAt", FieldValue.serverTimestamp());
            db.collection("submittedVideos").document(newVideoId).set(videoMeta);

            // Add this submittedVideo ID to the template's submittedVideos list (not scene data!)
            DocumentReference templateDoc = db.collection("templates").document(templateId);
            templateDoc.update("submittedVideos", FieldValue.arrayUnion(newVideoId));

            // Optionally update user's subscribedTemplates
            DocumentReference userDoc = db.collection("users").document(userId);
            userDoc.update("subscribedTemplates." + templateId, true);

            // --- Add notification to content manager (template owner) ---
            // Fetch template to get owner
            DocumentSnapshot templateSnap = templateDoc.get().get();
            if (templateSnap.exists() && templateSnap.contains("userId")) {
                String ownerId = templateSnap.getString("userId");
                if (ownerId != null && !ownerId.isEmpty()) {
                    DocumentReference ownerDoc = db.collection("users").document(ownerId);
                    String notifId = UUID.randomUUID().toString();
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("type", "new_submission");
                    notif.put("message", "HARDCODED_New video submission received for your template by user: " + userId); // TODO: Internationalize this message
                    notif.put("templateId", templateId);
                    notif.put("submittedBy", userId);
                    notif.put("timestamp", System.currentTimeMillis());
                    notif.put("read", false);
                    ownerDoc.update("notifications." + notifId, notif);
                }
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("videoId", newVideoId);
            responseData.put("videoUrl", videoUrl);
            responseData.put("similarityScore", similarityScore);
            responseData.put("suggestions", suggestions);
            responseData.put("publishStatus", "pending");
            
            String message = i18nService.getMessage("video.uploaded", language);
            return ResponseEntity.ok(ApiResponse.ok(message, responseData));
        } catch (IOException e) {
            String message = i18nService.getMessage("server.error", language);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(message, "Failed to upload/process video: " + e.getMessage()));
        }
    }
    
    private double calculateSimilarity(List<Map<String, String>> userScenes, List<Map<String, String>> exampleScenes) {
        if (userScenes == null || exampleScenes == null || userScenes.isEmpty() || exampleScenes.isEmpty()) {
            return 0.75; // Default similarity for demo
        }
        // Simple mock calculation - in real implementation, this would use AI comparison
        return 0.70 + (Math.random() * 0.25); // Random between 70-95%
    }
    
    
    private List<String> generateSuggestions(Map<String, String> exampleScene, Map<String, String> userScene, double similarity, String language) {
        try {
            EditSuggestionService.EditSuggestionRequest request = new EditSuggestionService.EditSuggestionRequest();
            request.setTemplateDescriptions(exampleScene);
            request.setUserDescriptions(userScene);
            
            // Create mock similarity scores
            Map<String, Double> scores = new HashMap<>();
            for (String key : exampleScene.keySet()) {
                scores.put(key, similarity + (Math.random() * 0.2 - 0.1)); // Add some variance
            }
            request.setSimilarityScores(scores);
            
            EditSuggestionService.EditSuggestionResponse response = editSuggestionService.generateSuggestions(request, language);
            return response.getSuggestions();
        } catch (Exception e) {
            System.err.println("Error generating suggestions: " + e.getMessage());
            return List.of("Unable to generate suggestions at this time");
        }
    }

}
