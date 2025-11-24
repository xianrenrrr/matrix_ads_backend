package com.example.demo.controller.contentcreator;

import com.example.demo.api.ApiResponse;
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
    private com.example.demo.dao.UserDao userDao;
    
    @Autowired
    private com.example.demo.dao.SubmittedVideoDao submittedVideoDao;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    @Autowired
    private com.example.demo.dao.SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired
    private com.example.demo.dao.CompiledVideoDao compiledVideoDao;
    
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
            // Get user's groupId using DAO
            com.example.demo.model.User user = userDao.findById(userId);
            if (user == null || user.getGroupId() == null) {
                return ResponseEntity.ok(ApiResponse.ok("No group assigned", Collections.emptyList()));
            }
            
            String groupId = user.getGroupId();
            
            // Get all active assignments for the group using DAO
            List<com.example.demo.model.TemplateAssignment> assignments = templateAssignmentDao.getAssignmentsByGroup(groupId);
            
            log.info("Found {} total assignments for group {}", assignments.size(), groupId);
        
            List<Map<String, Object>> pendingAssignments = new ArrayList<>();
            
            for (com.example.demo.model.TemplateAssignment assignment : assignments) {
                String assignmentId = assignment.getId();
                String compositeVideoId = userId + "_" + assignmentId;
                
                // Check expiry
                Date expiresAt = assignment.getExpiresAt();
                if (expiresAt != null && expiresAt.before(new Date())) {
                    continue; // Skip expired assignments
                }
                
                // Check if submittedVideo exists using DAO
                com.example.demo.model.SubmittedVideo submittedVideo = submittedVideoDao.findById(compositeVideoId);
                
                boolean shouldInclude = false;
                Map<String, Object> progress = null;
                
                if (submittedVideo == null) {
                    // No submission yet - include
                    shouldInclude = true;
                } else {
                    // Check status
                    String status = submittedVideo.getPublishStatus();
                    if ("pending".equals(status) || "approved".equals(status) || "rejected".equals(status)) {
                        shouldInclude = true;
                        progress = submittedVideo.getProgress();
                    }
                }
                
                if (shouldInclude) {
                    Map<String, Object> assignmentData = new HashMap<>();
                    assignmentData.put("id", assignmentId);
                    
                    // Get template info from snapshot
                    com.example.demo.model.ManualTemplate template = assignment.getTemplateSnapshot();
                    if (template != null) {
                        assignmentData.put("templateTitle", template.getTemplateTitle());
                        assignmentData.put("thumbnailUrl", template.getThumbnailUrl());
                        assignmentData.put("sceneCount", template.getScenes() != null ? template.getScenes().size() : 0);
                        assignmentData.put("totalVideoLength", template.getTotalVideoLength());
                    }
                    
                    assignmentData.put("expiresAt", expiresAt);
                    assignmentData.put("pushedAt", assignment.getPushedAt());
                    
                    // Calculate days until expiry
                    if (expiresAt != null) {
                        long daysUntilExpiry = (expiresAt.getTime() - System.currentTimeMillis()) / (1000 * 86400);
                        assignmentData.put("daysUntilExpiry", Math.max(0, daysUntilExpiry));
                    }
                    
                    // Add progress if exists
                    if (progress != null) {
                        assignmentData.put("progress", progress);
                    }
                    
                    pendingAssignments.add(assignmentData);
                }
            }
            
            // Sort by pushedAt descending (most recent first)
            pendingAssignments.sort((a, b) -> {
                Date timeA = (Date) a.get("pushedAt");
                Date timeB = (Date) b.get("pushedAt");
                if (timeA == null || timeB == null) return 0;
                return timeB.compareTo(timeA);
            });
            
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
            // Get all submittedVideos for this user using DAO
            List<com.example.demo.model.SubmittedVideo> submittedVideos = submittedVideoDao.findByUserId(userId);
        
            List<Map<String, Object>> toDownloadVideos = new ArrayList<>();
        
            for (com.example.demo.model.SubmittedVideo submittedVideo : submittedVideos) {
                // Filter for published status
                if (!"published".equals(submittedVideo.getPublishStatus())) {
                    continue;
                }
                
                Map<String, Object> video = new HashMap<>();
                video.put("id", submittedVideo.getId());
                video.put("videoId", submittedVideo.getId());  // For status update
                video.put("assignmentId", submittedVideo.getAssignmentId());
            
                // Get template info from assignment
                String assignmentId = submittedVideo.getAssignmentId();
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
                
                // Use compiled video URL from submittedVideo
                String compiledVideoUrl = submittedVideo.getCompiledVideoUrl();
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
                
                video.put("createdAt", submittedVideo.getCreatedAt());
                video.put("lastUpdated", submittedVideo.getLastUpdated());
            
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
            // Get all submittedVideos for this user using DAO
            List<com.example.demo.model.SubmittedVideo> submittedVideos = submittedVideoDao.findByUserId(userId);
        
            List<Map<String, Object>> downloadedVideos = new ArrayList<>();
        
            for (com.example.demo.model.SubmittedVideo submittedVideo : submittedVideos) {
                // Filter for downloaded status
                if (!"downloaded".equals(submittedVideo.getPublishStatus())) {
                    continue;
                }
                
                Map<String, Object> video = new HashMap<>();
                video.put("id", submittedVideo.getId());
                video.put("videoId", submittedVideo.getId());
                video.put("assignmentId", submittedVideo.getAssignmentId());
            
                // Get template info from assignment
                String assignmentId = submittedVideo.getAssignmentId();
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
                
                // Use compiled video URL from submittedVideo
                String compiledVideoUrl = submittedVideo.getCompiledVideoUrl();
                if (compiledVideoUrl != null) {
                    video.put("videoUrl", compiledVideoUrl);
                    // Generate signed URL for download
                    try {
                        String signedUrl = generateSignedUrl(compiledVideoUrl);
                        video.put("signedUrl", signedUrl);
                        video.put("compiledVideoUrl", signedUrl); // For mini app compatibility
                    } catch (Exception e) {
                        log.warn("Failed to generate signed URL: {}", e.getMessage());
                        video.put("signedUrl", compiledVideoUrl);
                        video.put("compiledVideoUrl", compiledVideoUrl);
                    }
                }
                
                video.put("downloadedAt", submittedVideo.getDownloadedAt());
                video.put("createdAt", submittedVideo.getCreatedAt());
            
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
        
        // Verify the video belongs to this user using DAO
        com.example.demo.model.SubmittedVideo video = submittedVideoDao.findById(videoId);
        if (video == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Video not found"));
        }
        
        if (!userId.equals(video.getUploadedBy())) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Unauthorized"));
        }
        
        if (!"published".equals(video.getPublishStatus())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.fail("Video must be in 'published' status to mark as downloaded"));
        }
        
        // Update status to "downloaded"
        video.setPublishStatus("downloaded");
        video.setDownloadedAt(new Date());
        video.setDownloadedBy(userId);
        video.setLastUpdated(new Date());
        
        submittedVideoDao.update(video);
        
        log.info("✅ Video {} marked as downloaded", videoId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("videoId", videoId);
        response.put("status", "downloaded");
        
        return ResponseEntity.ok(ApiResponse.ok("Video marked as downloaded", response));
    }
    
    /**
     * Get compiled video URL from compiledVideos collection
     * Note: This is deprecated - use submittedVideo.compiledVideoUrl instead
     */
    private String getCompiledVideoUrl(String submittedVideoId) {
        try {
            String[] parts = submittedVideoId.split("_");
            if (parts.length < 2) return null;
            
            // parts[0] = userId, parts[1] = templateId/assignmentId
            com.example.demo.model.CompiledVideo compiledVideo = compiledVideoDao.findByTemplateIdAndUserId(parts[1], parts[0]);
            
            if (compiledVideo != null) {
                return compiledVideo.getVideoUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to get compiled video URL: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Generate signed URL for video download
     * Uses the same method as content manager for consistency
     */
    private String generateSignedUrl(String videoUrl) {
        try {
            return sceneSubmissionDao.getSignedUrl(videoUrl);
        } catch (Exception e) {
            log.error("Failed to generate signed URL for {}: {}", videoUrl, e.getMessage());
            return videoUrl;
        }
    }
}
