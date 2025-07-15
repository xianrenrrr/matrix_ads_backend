package com.example.demo.controller.contentcreator;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-creator")
public class DashboardController {
    
    @Autowired
    private Firestore db;
    
    // Get dashboard statistics for content creator
    @GetMapping("/users/{userId}/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats(@PathVariable String userId) {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Get subscribed templates count
            DocumentReference userRef = db.collection("users").document(userId);
            DocumentSnapshot userSnap = userRef.get().get();
            int subscribedTemplatesCount = 0;
            
            if (userSnap.exists() && userSnap.contains("subscribed_template")) {
                Object raw = userSnap.get("subscribed_template");
                if (raw instanceof java.util.List<?>) {
                    subscribedTemplatesCount = ((java.util.List<?>) raw).size();
                }
            }
            
            // Get submitted videos count
            CollectionReference submittedVideosRef = db.collection("submittedVideos");
            Query submittedQuery = submittedVideosRef.whereEqualTo("uploadedBy", userId);
            ApiFuture<QuerySnapshot> submittedSnapshot = submittedQuery.get();
            int submittedVideosCount = submittedSnapshot.get().getDocuments().size();
            
            // Get published videos count (approved submissions)
            Query publishedQuery = submittedVideosRef
                .whereEqualTo("uploadedBy", userId)
                .whereEqualTo("feedback.publishStatus", "approved");
            ApiFuture<QuerySnapshot> publishedSnapshot = publishedQuery.get();
            int publishedVideosCount = publishedSnapshot.get().getDocuments().size();
            
            // Build response
            stats.put("availableTemplates", subscribedTemplatesCount);
            stats.put("recordedVideos", submittedVideosCount);
            stats.put("publishedVideos", publishedVideosCount);
            stats.put("success", true);
            
            return ResponseEntity.ok(stats);
            
        } catch (InterruptedException | ExecutionException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to fetch dashboard stats: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    // Get recent activity for content creator
    @GetMapping("/users/{userId}/recent-activity")
    public ResponseEntity<Map<String, Object>> getRecentActivity(@PathVariable String userId) {
        try {
            Map<String, Object> response = new HashMap<>();
            java.util.List<Map<String, Object>> activities = new java.util.ArrayList<>();
            
            // Get recent submissions (last 5)
            CollectionReference submittedVideosRef = db.collection("submittedVideos");
            Query recentQuery = submittedVideosRef
                .whereEqualTo("uploadedBy", userId)
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .limit(5);
            
            ApiFuture<QuerySnapshot> querySnapshot = recentQuery.get();
            for (DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
                Map<String, Object> data = doc.getData();
                if (data != null) {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("type", "submission");
                    activity.put("templateId", data.get("templateId"));
                    activity.put("videoUrl", data.get("videoUrl"));
                    activity.put("submittedAt", data.get("submittedAt"));
                    activity.put("status", getSubmissionStatus(data));
                    activities.add(activity);
                }
            }
            
            response.put("success", true);
            response.put("activities", activities);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to fetch recent activity: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    private String getSubmissionStatus(Map<String, Object> submissionData) {
        Object feedbackObj = submissionData.get("feedback");
        if (feedbackObj instanceof Map<?, ?>) {
            Map<?, ?> feedback = (Map<?, ?>) feedbackObj;
            Object publishStatus = feedback.get("publishStatus");
            if (publishStatus != null) {
                return publishStatus.toString();
            }
        }
        return "pending";
    }
}