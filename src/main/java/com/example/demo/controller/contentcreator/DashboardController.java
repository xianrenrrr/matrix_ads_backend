package com.example.demo.controller.contentcreator;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
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
    
    @Autowired
    private I18nService i18nService;
    
    // Get dashboard statistics for content creator
    @GetMapping("/users/{userId}/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(@PathVariable String userId,
                                                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
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
            
            String message = i18nService.getMessage("operation.success", language);
            return ResponseEntity.ok(ApiResponse.ok(message, stats));
            
        } catch (InterruptedException | ExecutionException e) {
            String language = i18nService.detectLanguageFromHeader(acceptLanguage);
            String message = i18nService.getMessage("server.error", language);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.fail(message, "Failed to fetch dashboard stats: " + e.getMessage()));
        }
    }
    
}