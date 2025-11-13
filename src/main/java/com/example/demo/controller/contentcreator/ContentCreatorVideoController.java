package com.example.demo.controller.contentcreator;

import com.example.demo.api.ApiResponse;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.FieldValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Content Creator Video Controller
 * Handles video status and downloads for content creators
 */
@RestController
@RequestMapping("/content-creator")
public class ContentCreatorVideoController {
    
    private static final Logger log = LoggerFactory.getLogger(ContentCreatorVideoController.class);
    
    @Autowired
    private Firestore db;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    /**
     * Get user's pending assignments (待录制)
     * Returns templates that need recording:
     * - No submittedVideo exists, OR
     * - submittedVideo status is pending/approved/rejected
     */
    @GetMapping("/users/{userId}/assignments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserAssignments(
            @PathVariable String userId) {
        
        log.info("Getting assignments for user: {}", userId);
        
        try {
            // Get user's groupId
            String groupId = db.collection("users").document(userId).get().get().getString("groupId");
            if (groupId == null) {
                return ResponseEntity.ok(ApiResponse.ok("No group assigned", Collections.emptyList()));
            }
            
            // Get all active assignments for the group (simplified query - no composite index needed)
            QuerySnapshot assignmentsSnapshot = db.collection("templateAssignments")
                    .whereEqualTo("groupId", groupId)
                    .orderBy("pushedAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .get();
        
            List<Map<String, Object>> pendingAssignments = new ArrayList<>();
            
            for (QueryDocumentSnapshot assignmentDoc : assignmentsSnapshot.getDocuments()) {
                String assignmentId = assignmentDoc.getId();
                String compositeVideoId = userId + "_" + assignmentId;
                
                // Check expiry (filter in code since we removed from query)
                com.google.cloud.Timestamp expiresAt = assignmentDoc.getTimestamp("expiresAt");
                if (expiresAt != null && expiresAt.getSeconds() < System.currentTimeMillis() / 1000) {
                    continue; // Skip expired assignments
                }
                
                // Check if submittedVideo exists
                var submittedVideoDoc = db.collection("submittedVideos")
                        .document(compositeVideoId)
                        .get()
                        .get();
                
                boolean shouldInclude = false;
                Map<String, Object> progress = null;
                
                if (!submittedVideoDoc.exists()) {
                    // No submission yet - include
                    shouldInclude = true;
                } else {
                    // Check status
                    String status = submittedVideoDoc.getString("publishStatus");
                    if ("pending".equals(status) || "approved".equals(status) || "rejected".equals(status)) {
                        shouldInclude = true;
                        progress = (Map<String, Object>) submittedVideoDoc.get("progress");
                    }
                }
                
                if (shouldInclude) {
                    Map<String, Object> assignment = new HashMap<>();
                    assignment.put("id", assignmentId);
                    
                    // Get template info from snapshot
                    Map<String, Object> snapshot = (Map<String, Object>) assignmentDoc.get("templateSnapshot");
                    if (snapshot != null) {
                        assignment.put("templateTitle", snapshot.get("templateTitle"));
                        assignment.put("thumbnailUrl", snapshot.get("thumbnailUrl"));
                        
                        List<Map<String, Object>> scenes = (List<Map<String, Object>>) snapshot.get("scenes");
                        assignment.put("sceneCount", scenes != null ? scenes.size() : 0);
                        assignment.put("totalVideoLength", snapshot.get("totalVideoLength"));
                    }
                    
                    assignment.put("expiresAt", expiresAt);
                    assignment.put("pushedAt", assignmentDoc.getTimestamp("pushedAt"));
                    
                    // Calculate days until expiry
                    if (expiresAt != null) {
                        long daysUntilExpiry = (expiresAt.getSeconds() - System.currentTimeMillis() / 1000) / 86400;
                        assignment.put("daysUntilExpiry", Math.max(0, daysUntilExpiry));
                    }
                    
                    // Add progress if exists
                    if (progress != null) {
                        assignment.put("progress", progress);
                    }
                    
                    pendingAssignments.add(assignment);
                }
            }
            
            log.info("Found {} pending assignments for user {}", pendingAssignments.size(), userId);
            return ResponseEntity.ok(ApiResponse.ok("Assignments retrieved", pendingAssignments));
            
        } catch (Exception e) {
            log.error("Error getting assignments for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.fail("Failed to get assignments: " + e.getMessage()));
        }
    }
    
    /**
     * Get user's compiled videos ready for download (待下载)
     * Returns submittedVideos with status = "published"
     */
    @GetMapping("/users/{userId}/to-download")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getToDownloadVideos(
            @PathVariable String userId) {
        
        log.info("Getting to-download videos for user: {}", userId);
        
        try {
            // Get all submittedVideos for this user (filter by document ID pattern)
            // Document ID format: {userId}_{assignmentId}
            QuerySnapshot videosSnapshot = db.collection("submittedVideos")
                    .whereEqualTo("uploadedBy", userId)
                    .limit(100)
                    .get()
                    .get();
        
            List<Map<String, Object>> toDownloadVideos = new ArrayList<>();
        
            for (QueryDocumentSnapshot videoDoc : videosSnapshot.getDocuments()) {
                // Filter for published status
                String status = videoDoc.getString("publishStatus");
                if (!"published".equals(status)) {
                    continue;
                }
            Map<String, Object> video = new HashMap<>();
            video.put("id", videoDoc.getId());
            video.put("videoId", videoDoc.getId());  // For status update
            video.put("assignmentId", videoDoc.getString("assignmentId"));
            
            // Get template info from assignment
            String assignmentId = videoDoc.getString("assignmentId");
            if (assignmentId != null) {
                try {
                    var assignment = templateAssignmentDao.getAssignment(assignmentId);
                    if (assignment != null && assignment.getTemplateSnapshot() != null) {
                        video.put("templateTitle", assignment.getTemplateSnapshot().getTemplateTitle());
                        video.put("thumbnailUrl", assignment.getTemplateSnapshot().getThumbnailUrl());
                        video.put("sceneCount", assignment.getTemplateSnapshot().getScenes() != null ? 
                                assignment.getTemplateSnapshot().getScenes().size() : 0);
                        video.put("duration", assignment.getTemplateSnapshot().getTotalVideoLength());
                    }
                } catch (Exception e) {
                    log.warn("Failed to get assignment info: {}", e.getMessage());
                }
            }
            
            // Get compiled video URL from compiledVideos collection
            String compiledVideoUrl = getCompiledVideoUrl(videoDoc.getId());
            if (compiledVideoUrl != null) {
                video.put("videoUrl", compiledVideoUrl);
                // Generate signed URL for download
                try {
                    String signedUrl = generateSignedUrl(compiledVideoUrl);
                    video.put("signedUrl", signedUrl);
                } catch (Exception e) {
                    log.warn("Failed to generate signed URL: {}", e.getMessage());
                    video.put("signedUrl", compiledVideoUrl);
                }
            }
            
            video.put("createdAt", videoDoc.getTimestamp("createdAt"));
            video.put("lastUpdated", videoDoc.getTimestamp("lastUpdated"));
            
                toDownloadVideos.add(video);
            }
            
            log.info("Found {} videos to download for user {}", toDownloadVideos.size(), userId);
            return ResponseEntity.ok(ApiResponse.ok("Videos retrieved", toDownloadVideos));
            
        } catch (Exception e) {
            log.error("Error getting to-download videos for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.fail("Failed to get videos: " + e.getMessage()));
        }
    }
    
    /**
     * Get user's downloaded videos (已下载)
     * Returns submittedVideos with status = "downloaded"
     */
    @GetMapping("/users/{userId}/downloaded")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDownloadedVideos(
            @PathVariable String userId) {
        
        log.info("Getting downloaded videos for user: {}", userId);
        
        try {
            // Get all submittedVideos for this user (filter by status in code)
            QuerySnapshot videosSnapshot = db.collection("submittedVideos")
                    .whereEqualTo("uploadedBy", userId)
                    .limit(100)
                    .get()
                    .get();
        
            List<Map<String, Object>> downloadedVideos = new ArrayList<>();
        
            for (QueryDocumentSnapshot videoDoc : videosSnapshot.getDocuments()) {
                // Filter for downloaded status
                String status = videoDoc.getString("publishStatus");
                if (!"downloaded".equals(status)) {
                    continue;
                }
            Map<String, Object> video = new HashMap<>();
            video.put("id", videoDoc.getId());
            video.put("videoId", videoDoc.getId());
            video.put("assignmentId", videoDoc.getString("assignmentId"));
            
            // Get template info from assignment
            String assignmentId = videoDoc.getString("assignmentId");
            if (assignmentId != null) {
                try {
                    var assignment = templateAssignmentDao.getAssignment(assignmentId);
                    if (assignment != null && assignment.getTemplateSnapshot() != null) {
                        video.put("templateTitle", assignment.getTemplateSnapshot().getTemplateTitle());
                        video.put("thumbnailUrl", assignment.getTemplateSnapshot().getThumbnailUrl());
                        video.put("sceneCount", assignment.getTemplateSnapshot().getScenes() != null ? 
                                assignment.getTemplateSnapshot().getScenes().size() : 0);
                        video.put("duration", assignment.getTemplateSnapshot().getTotalVideoLength());
                    }
                } catch (Exception e) {
                    log.warn("Failed to get assignment info: {}", e.getMessage());
                }
            }
            
            video.put("downloadedAt", videoDoc.getTimestamp("downloadedAt"));
            video.put("createdAt", videoDoc.getTimestamp("createdAt"));
            
                downloadedVideos.add(video);
            }
            
            log.info("Found {} downloaded videos for user {}", downloadedVideos.size(), userId);
            return ResponseEntity.ok(ApiResponse.ok("Videos retrieved", downloadedVideos));
            
        } catch (Exception e) {
            log.error("Error getting downloaded videos for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.fail("Failed to get videos: " + e.getMessage()));
        }
    }
    
    /**
     * Mark video as downloaded (content creator only)
     * Updates submittedVideo status from "published" to "downloaded"
     */
    @PostMapping("/submitted-videos/{videoId}/mark-downloaded")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAsDownloaded(
            @PathVariable String videoId,
            @RequestBody Map<String, String> request) throws ExecutionException, InterruptedException {
        
        String userId = request.get("userId");
        log.info("Marking video {} as downloaded by user {}", videoId, userId);
        
        // Verify the video belongs to this user
        var videoDoc = db.collection("submittedVideos").document(videoId).get().get();
        if (!videoDoc.exists()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Video not found"));
        }
        
        String uploadedBy = videoDoc.getString("uploadedBy");
        if (!userId.equals(uploadedBy)) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Unauthorized"));
        }
        
        String currentStatus = videoDoc.getString("publishStatus");
        if (!"published".equals(currentStatus)) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.fail("Video must be in 'published' status to mark as downloaded"));
        }
        
        // Update status to "downloaded"
        Map<String, Object> updates = new HashMap<>();
        updates.put("publishStatus", "downloaded");
        updates.put("downloadedAt", FieldValue.serverTimestamp());
        updates.put("downloadedBy", userId);
        
        db.collection("submittedVideos").document(videoId).update(updates).get();
        
        log.info("✅ Video {} marked as downloaded", videoId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("videoId", videoId);
        response.put("status", "downloaded");
        
        return ResponseEntity.ok(ApiResponse.ok("Video marked as downloaded", response));
    }
    
    /**
     * Get compiled video URL from compiledVideos collection
     */
    private String getCompiledVideoUrl(String submittedVideoId) {
        try {
            QuerySnapshot compiledSnapshot = db.collection("compiledVideos")
                    .whereEqualTo("userId", submittedVideoId.split("_")[0])
                    .whereEqualTo("templateId", submittedVideoId.split("_")[1])
                    .limit(1)
                    .get()
                    .get();
            
            if (!compiledSnapshot.isEmpty()) {
                return compiledSnapshot.getDocuments().get(0).getString("videoUrl");
            }
        } catch (Exception e) {
            log.warn("Failed to get compiled video URL: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Generate signed URL for video download
     * TODO: Implement actual signed URL generation with expiry
     */
    private String generateSignedUrl(String videoUrl) {
        // For now, return the original URL
        // In production, generate a signed URL with expiry time
        return videoUrl;
    }
}
