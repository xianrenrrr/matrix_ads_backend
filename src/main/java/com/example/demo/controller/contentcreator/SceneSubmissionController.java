package com.example.demo.controller.contentcreator;

import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.SceneSubmission;
import com.example.demo.model.ManualTemplate;
import com.example.demo.service.FirebaseStorageService;
import com.example.demo.ai.EditSuggestionService;
import com.example.demo.ai.comparison.VideoComparisonIntegrationService;
import com.example.demo.ai.scene.SceneAnalysisService;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Content Creator Scene Submission Controller
 * Handles individual scene uploads, resubmissions, and progress tracking
 */
@RestController
@RequestMapping("/content-creator/scenes")
public class SceneSubmissionController {
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired(required = false)
    private FirebaseStorageService firebaseStorageService;
    
    @Autowired
    private EditSuggestionService editSuggestionService;
    
    @Autowired
    private VideoComparisonIntegrationService videoComparisonService;
    
    @Autowired
    private SceneAnalysisService sceneAnalysisService;
    
    @Autowired
    private Firestore db;
    
    /**
     * Upload a single scene for a template
     * POST /content-creator/scenes/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScene(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") String templateId,
            @RequestParam("userId") String userId,
            @RequestParam("sceneNumber") int sceneNumber,
            @RequestParam(value = "sceneTitle", required = false) String sceneTitle) {
        
        try {
            // Validate inputs
            if (file.isEmpty()) {
                return createErrorResponse("File is empty", HttpStatus.BAD_REQUEST);
            }
            
            // Get template to validate scene number and get scene data
            ManualTemplate template = templateDao.getTemplate(templateId);
            if (template == null) {
                return createErrorResponse("Template not found", HttpStatus.NOT_FOUND);
            }
            
            if (sceneNumber < 1 || sceneNumber > template.getScenes().size()) {
                return createErrorResponse("Invalid scene number", HttpStatus.BAD_REQUEST);
            }
            
            // Get scene data from template
            com.example.demo.model.Scene templateScene = template.getScenes().get(sceneNumber - 1);
            String expectedSceneTitle = templateScene.getSceneTitle();
            
            // Check if user already has a submission for this scene
            SceneSubmission existingSubmission = sceneSubmissionDao.findByTemplateIdAndUserIdAndSceneNumber(
                templateId, userId, sceneNumber);
            
            // Allow resubmission for both pending and approved scenes (no blocking)
            
            // Create composite video ID using userId + templateId
            String compositeVideoId = userId + "_" + templateId;
            
            // Upload file to Firebase Storage with scene-specific naming
            String sceneVideoId = UUID.randomUUID().toString();
            FirebaseStorageService.UploadResult uploadResult = firebaseStorageService.uploadVideoWithThumbnail(file, userId, sceneVideoId);
            String videoUrl = uploadResult.videoUrl;
            
            // Get thumbnail from upload result
            String thumbnailUrl = uploadResult.thumbnailUrl;
            
            // Create scene submission
            SceneSubmission sceneSubmission = new SceneSubmission(templateId, userId, sceneNumber, 
                sceneTitle != null ? sceneTitle : expectedSceneTitle);
            
            sceneSubmission.setVideoUrl(videoUrl);
            sceneSubmission.setThumbnailUrl(thumbnailUrl);
            sceneSubmission.setOriginalFileName(file.getOriginalFilename());
            sceneSubmission.setFileSize(file.getSize());
            sceneSubmission.setFormat(getFileExtension(file.getOriginalFilename()));
            
            // Set template scene data for reference
            Map<String, Object> sceneDataMap = new HashMap<>();
            sceneDataMap.put("sceneTitle", templateScene.getSceneTitle());
            sceneDataMap.put("scriptLine", templateScene.getScriptLine());
            sceneDataMap.put("sceneDuration", templateScene.getSceneDurationInSeconds());
            sceneDataMap.put("presenceOfPerson", templateScene.isPresenceOfPerson());
            sceneDataMap.put("deviceOrientation", templateScene.getDeviceOrientation());
            sceneDataMap.put("movementInstructions", templateScene.getMovementInstructions());
            sceneSubmission.setTemplateSceneData(sceneDataMap);
            sceneSubmission.setSceneInstructions(templateScene.getScriptLine());
            sceneSubmission.setExpectedDuration(String.valueOf(templateScene.getSceneDurationInSeconds()));
            
            // Handle resubmission (both pending with feedback and approved can be resubmitted)
            if (existingSubmission != null) {
                sceneSubmission.setPreviousSubmissionId(existingSubmission.getId());
                sceneSubmission.setResubmissionCount(existingSubmission.getResubmissionCount() + 1);
                
                // Add to resubmission history
                List<String> history = existingSubmission.getResubmissionHistory();
                if (history == null) {
                    history = new ArrayList<>();
                }
                history.add(existingSubmission.getId());
                sceneSubmission.setResubmissionHistory(history);
            }
            
            // Perform AI analysis
            performAIAnalysis(sceneSubmission, templateScene);
            
            // Save to database
            String sceneId = sceneSubmissionDao.save(sceneSubmission);
            sceneSubmission.setId(sceneId);
            
            // Save/update scene in submittedVideos collection
            try {
                updateSubmittedVideoWithScene(compositeVideoId, templateId, userId, sceneSubmission);
            } catch (Exception e) {
                System.err.println("Error updating submittedVideos: " + e.getMessage());
                // Continue even if this fails - the scene is still saved in sceneSubmissions
            }
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Scene uploaded successfully");
            response.put("sceneSubmission", sceneSubmission);
            response.put("similarityScore", sceneSubmission.getSimilarityScore());
            response.put("aiSuggestions", sceneSubmission.getAiSuggestions());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error uploading scene: " + e.getMessage());
            return createErrorResponse("Failed to upload scene: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get all scene submissions for a user's template
     * GET /content-creator/scenes/template/{templateId}/user/{userId}
     */
    @GetMapping("/template/{templateId}/user/{userId}")
    public ResponseEntity<Map<String, Object>> getSceneSubmissions(
            @PathVariable String templateId,
            @PathVariable String userId) {
        
        try {
            List<SceneSubmission> submissions = sceneSubmissionDao.findByTemplateIdAndUserId(templateId, userId);
            
            // Get template to know total scenes expected
            ManualTemplate template = templateDao.getTemplate(templateId);
            int totalScenes = template != null ? template.getScenes().size() : 0;
            
            // Calculate progress
            int approvedCount = (int) submissions.stream().filter(s -> "approved".equals(s.getStatus())).count();
            int pendingCount = (int) submissions.stream().filter(s -> "pending".equals(s.getStatus())).count();
            int rejectedCount = (int) submissions.stream().filter(s -> "rejected".equals(s.getStatus())).count();
            int submittedCount = submissions.size();
            
            // Group submissions by scene number for easy access
            Map<Integer, SceneSubmission> sceneMap = new HashMap<>();
            for (SceneSubmission submission : submissions) {
                sceneMap.put(submission.getSceneNumber(), submission);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("templateId", templateId);
            response.put("userId", userId);
            response.put("submissions", submissions);
            response.put("sceneMap", sceneMap);
            response.put("progress", createProgressMap(totalScenes, submittedCount, approvedCount, pendingCount, rejectedCount));
            response.put("isReadyForCompilation", approvedCount == totalScenes && totalScenes > 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get scene submissions: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get submitted video data by composite ID (userId_templateId)
     * GET /content-creator/scenes/submitted-videos/{compositeVideoId}
     */
    @GetMapping("/submitted-videos/{compositeVideoId}")
    public ResponseEntity<Map<String, Object>> getSubmittedVideo(@PathVariable String compositeVideoId) {
        try {
            // Get video document from submittedVideos collection
            DocumentReference videoDocRef = db.collection("submittedVideos").document(compositeVideoId);
            DocumentSnapshot videoDoc = videoDocRef.get().get();
            
            if (!videoDoc.exists()) {
                return createErrorResponse("No submission found for this template", HttpStatus.NOT_FOUND);
            }
            
            Map<String, Object> videoData = new HashMap<>(videoDoc.getData());
            
            // Fetch full scene details from sceneSubmissions collection using scene IDs
            @SuppressWarnings("unchecked")
            Map<String, Object> scenes = (Map<String, Object>) videoData.get("scenes");
            if (scenes != null && !scenes.isEmpty()) {
                Map<String, Object> fullScenes = new HashMap<>();
                
                for (Map.Entry<String, Object> entry : scenes.entrySet()) {
                    String sceneNumber = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sceneRef = (Map<String, Object>) entry.getValue();
                    String sceneId = (String) sceneRef.get("sceneId");
                    
                    if (sceneId != null) {
                        // Fetch full scene data from sceneSubmissions collection
                        SceneSubmission sceneSubmission = sceneSubmissionDao.findById(sceneId);
                        if (sceneSubmission != null) {
                            Map<String, Object> fullSceneData = new HashMap<>();
                            fullSceneData.put("sceneId", sceneSubmission.getId());
                            fullSceneData.put("sceneNumber", sceneSubmission.getSceneNumber());
                            fullSceneData.put("sceneTitle", sceneSubmission.getSceneTitle());
                            fullSceneData.put("videoUrl", sceneSubmission.getVideoUrl());
                            fullSceneData.put("thumbnailUrl", sceneSubmission.getThumbnailUrl());
                            fullSceneData.put("status", sceneSubmission.getStatus());
                            fullSceneData.put("similarityScore", sceneSubmission.getSimilarityScore());
                            fullSceneData.put("aiSuggestions", sceneSubmission.getAiSuggestions());
                            fullSceneData.put("submittedAt", sceneSubmission.getSubmittedAt());
                            fullSceneData.put("originalFileName", sceneSubmission.getOriginalFileName());
                            fullSceneData.put("fileSize", sceneSubmission.getFileSize());
                            fullSceneData.put("format", sceneSubmission.getFormat());
                            fullScenes.put(sceneNumber, fullSceneData);
                        } else {
                            // Keep the minimal data if scene not found
                            fullScenes.put(sceneNumber, sceneRef);
                        }
                    }
                }
                
                videoData.put("scenes", fullScenes);
            }
            
            // Return the video data with full scene details
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.putAll(videoData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get submitted video: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get specific scene submission details
     * GET /content-creator/scenes/{sceneId}
     */
    @GetMapping("/{sceneId}")
    public ResponseEntity<Map<String, Object>> getSceneSubmission(@PathVariable String sceneId) {
        try {
            SceneSubmission submission = sceneSubmissionDao.findById(sceneId);
            
            if (submission == null) {
                return createErrorResponse("Scene submission not found", HttpStatus.NOT_FOUND);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sceneSubmission", submission);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get scene submission: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Resubmit a rejected scene
     * POST /content-creator/scenes/{sceneId}/resubmit
     */
    @PostMapping("/{sceneId}/resubmit")
    public ResponseEntity<Map<String, Object>> resubmitScene(
            @PathVariable String sceneId,
            @RequestParam("file") MultipartFile file) {
        
        try {
            // Get original submission
            SceneSubmission originalSubmission = sceneSubmissionDao.findById(sceneId);
            if (originalSubmission == null) {
                return createErrorResponse("Original scene submission not found", HttpStatus.NOT_FOUND);
            }
            
            if (!"rejected".equals(originalSubmission.getStatus())) {
                return createErrorResponse("Can only resubmit rejected scenes", HttpStatus.BAD_REQUEST);
            }
            
            // Create new submission using upload logic
            return uploadScene(file, originalSubmission.getTemplateId(), originalSubmission.getUserId(), 
                originalSubmission.getSceneNumber(), originalSubmission.getSceneTitle());
            
        } catch (Exception e) {
            return createErrorResponse("Failed to resubmit scene: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get scene progress for user across all templates
     * GET /content-creator/scenes/user/{userId}/progress
     */
    @GetMapping("/user/{userId}/progress")
    public ResponseEntity<Map<String, Object>> getUserSceneProgress(@PathVariable String userId) {
        try {
            List<SceneSubmission> allSubmissions = sceneSubmissionDao.findByUserId(userId);
            
            // Group by template ID
            Map<String, List<SceneSubmission>> submissionsByTemplate = new HashMap<>();
            for (SceneSubmission submission : allSubmissions) {
                submissionsByTemplate.computeIfAbsent(submission.getTemplateId(), k -> new ArrayList<>())
                    .add(submission);
            }
            
            // Calculate progress for each template
            Map<String, Map<String, Object>> templateProgress = new HashMap<>();
            for (Map.Entry<String, List<SceneSubmission>> entry : submissionsByTemplate.entrySet()) {
                String templateId = entry.getKey();
                List<SceneSubmission> submissions = entry.getValue();
                
                try {
                    ManualTemplate template = templateDao.getTemplate(templateId);
                    int totalScenes = template != null ? template.getScenes().size() : 0;
                    
                    int approved = (int) submissions.stream().filter(s -> "approved".equals(s.getStatus())).count();
                    int pending = (int) submissions.stream().filter(s -> "pending".equals(s.getStatus())).count();
                    int rejected = (int) submissions.stream().filter(s -> "rejected".equals(s.getStatus())).count();
                    
                    templateProgress.put(templateId, createProgressMap(totalScenes, submissions.size(), 
                        approved, pending, rejected));
                        
                } catch (Exception e) {
                    System.err.println("Error calculating progress for template " + templateId + ": " + e.getMessage());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", userId);
            response.put("totalSubmissions", allSubmissions.size());
            response.put("templateProgress", templateProgress);
            response.put("recentSubmissions", allSubmissions.stream()
                .sorted((s1, s2) -> s2.getSubmittedAt().compareTo(s1.getSubmittedAt()))
                .limit(10)
                .toArray());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return createErrorResponse("Failed to get user progress: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    // Helper Methods
    
    @SuppressWarnings("unchecked")
    private void updateSubmittedVideoWithScene(String compositeVideoId, String templateId, String userId, SceneSubmission sceneSubmission) throws ExecutionException, InterruptedException {
        DocumentReference videoDocRef = db.collection("submittedVideos").document(compositeVideoId);
        DocumentSnapshot videoDoc = videoDocRef.get().get();
        
        // Only keep scene ID and status in submittedVideos document
        Map<String, Object> sceneData = new HashMap<>();
        sceneData.put("sceneId", sceneSubmission.getId());
        sceneData.put("status", sceneSubmission.getStatus());
        
        if (videoDoc.exists()) {
            // Update existing document - replace the scene or add it
            Map<String, Object> currentScenes = (Map<String, Object>) videoDoc.get("scenes");
            if (currentScenes == null) {
                currentScenes = new HashMap<>();
            }
            
            // Update the specific scene
            currentScenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            
            // Update the document
            Map<String, Object> updates = new HashMap<>();
            updates.put("scenes", currentScenes);
            updates.put("lastUpdated", com.google.cloud.firestore.FieldValue.serverTimestamp());
            
            // Calculate overall progress (only pending and approved now)
            int totalScenes = currentScenes.size();
            int approvedScenes = 0;
            int pendingScenes = 0;
            
            for (Object sceneObj : currentScenes.values()) {
                if (sceneObj instanceof Map) {
                    Map<String, Object> scene = (Map<String, Object>) sceneObj;
                    String status = (String) scene.get("status");
                    if ("approved".equals(status)) approvedScenes++;
                    else if ("pending".equals(status)) pendingScenes++;
                }
            }
            
            updates.put("progress", Map.of(
                "totalScenes", totalScenes,
                "approved", approvedScenes,
                "pending", pendingScenes,
                "completionPercentage", totalScenes > 0 ? (double) approvedScenes / totalScenes * 100 : 0
            ));
            
            // Automatically update publishStatus when all scenes are approved
            String currentPublishStatus = (String) videoDoc.get("publishStatus");
            if (approvedScenes == totalScenes && totalScenes > 0 && !"approved".equals(currentPublishStatus) && !"published".equals(currentPublishStatus)) {
                updates.put("publishStatus", "approved");
                updates.put("approvedAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
                System.out.println("Automatically updated publishStatus to 'approved' for video: " + compositeVideoId);
            }
            
            videoDocRef.update(updates);
        } else {
            // Create new document
            Map<String, Object> videoData = new HashMap<>();
            videoData.put("videoId", compositeVideoId);
            videoData.put("templateId", templateId);
            videoData.put("uploadedBy", userId);
            videoData.put("publishStatus", "pending");
            videoData.put("requestedApproval", true);
            videoData.put("createdAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
            videoData.put("lastUpdated", com.google.cloud.firestore.FieldValue.serverTimestamp());
            
            // Add the first scene
            Map<String, Object> scenes = new HashMap<>();
            scenes.put(String.valueOf(sceneSubmission.getSceneNumber()), sceneData);
            videoData.put("scenes", scenes);
            
            // Initial progress (only pending and approved now)
            videoData.put("progress", Map.of(
                "totalScenes", 1,
                "approved", 0,
                "pending", 1,
                "completionPercentage", 0.0
            ));
            
            videoDocRef.set(videoData);
            
            // Add this submittedVideo ID to the template's submittedVideos list (not scene data!)
            DocumentReference templateDoc = db.collection("templates").document(templateId);
            templateDoc.update("submittedVideos", FieldValue.arrayUnion(compositeVideoId));
        }
    }
    
    private void performAIAnalysis(SceneSubmission sceneSubmission, com.example.demo.model.Scene templateScene) {
        try {
            // Perform comprehensive AI analysis using the enhanced scene analysis service
            Map<String, Object> qualityMetrics = sceneAnalysisService.analyzeSceneQuality(sceneSubmission);
            sceneSubmission.setQualityMetrics(qualityMetrics);
            
            // Compare scene to template
            Map<String, Object> sceneMap = new HashMap<>();
            sceneMap.put("sceneTitle", templateScene.getSceneTitle());
            sceneMap.put("scriptLine", templateScene.getScriptLine());
            sceneMap.put("sceneDuration", templateScene.getSceneDurationInSeconds());
            double similarityScore = sceneAnalysisService.compareSceneToTemplate(sceneSubmission, sceneMap);
            sceneSubmission.setSimilarityScore(similarityScore);
            
            // Generate AI suggestions based on analysis
            List<String> suggestions = sceneAnalysisService.generateSceneImprovementSuggestions(sceneSubmission, similarityScore);
            sceneSubmission.setAiSuggestions(suggestions);
            
            System.out.println("AI Analysis completed for scene " + sceneSubmission.getSceneNumber() + 
                " - Similarity: " + Math.round(similarityScore * 100) + "%, Quality: " + 
                qualityMetrics.getOrDefault("qualityRating", "Unknown"));
            
        } catch (Exception e) {
            System.err.println("Error performing AI analysis: " + e.getMessage());
            // Set fallback values
            sceneSubmission.setSimilarityScore(0.75);
            sceneSubmission.setAiSuggestions(Arrays.asList("AI analysis temporarily unavailable - please try again later"));
            
            // Basic quality metrics as fallback
            Map<String, Object> fallbackMetrics = new HashMap<>();
            fallbackMetrics.put("overallQuality", 0.7);
            fallbackMetrics.put("qualityRating", "Good");
            fallbackMetrics.put("error", "Partial analysis failure");
            sceneSubmission.setQualityMetrics(fallbackMetrics);
        }
    }
    
    private Map<String, Object> createProgressMap(int totalScenes, int submitted, int approved, int pending, int rejected) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("totalScenes", totalScenes);
        progress.put("submitted", submitted);
        progress.put("approved", approved);
        progress.put("pending", pending);
        progress.put("rejected", rejected);
        progress.put("remaining", Math.max(0, totalScenes - submitted));
        progress.put("completionPercentage", totalScenes > 0 ? (double) approved / totalScenes * 100 : 0);
        progress.put("isComplete", approved == totalScenes && totalScenes > 0);
        return progress;
    }
    
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return "mp4";
    }
    
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}