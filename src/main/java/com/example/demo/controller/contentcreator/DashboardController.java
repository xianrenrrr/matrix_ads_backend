package com.example.demo.controller.contentcreator;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.example.demo.api.ApiResponse;
import com.example.demo.dao.GroupDao;
import com.example.demo.service.I18nService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/content-creator")
public class DashboardController {
    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    
    @Autowired
    private Firestore db;
    
    @Autowired
    private GroupDao groupDao;
    
    @Autowired
    private I18nService i18nService;
    
    // Get dashboard statistics for content creator
    @GetMapping("/users/{userId}/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(@PathVariable String userId,
                                                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Map<String, Object> stats = new HashMap<>();
        
        // Get user data
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userSnap = userRef.get().get();
        
        // Get subscribed templates count
        int subscribedTemplatesCount = 0;
        if (userSnap.exists() && userSnap.contains("subscribed_Templates")) {
            Map<String, Boolean> subscribed = (Map<String, Boolean>) userSnap.get("subscribed_Templates");
            if (subscribed != null) {
                // Count only active subscriptions (value = true)
                subscribedTemplatesCount = (int) subscribed.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .count();
            }
        }
        
        // Get available templates through group
        int availableTemplatesCount = 0;
        String userGroupId = groupDao.getUserGroupId(userId);
        if (userGroupId != null) {
            // Count templates assigned to user's group
            Query templateQuery = db.collection("templates")
                .whereArrayContains("assignedGroups", userGroupId);
            QuerySnapshot templateSnapshot = templateQuery.get().get();
            availableTemplatesCount = templateSnapshot.size();
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
        stats.put("subscribedTemplates", subscribedTemplatesCount);
        stats.put("availableTemplates", availableTemplatesCount);
        stats.put("recordedVideos", submittedVideosCount);
        stats.put("publishedVideos", publishedVideosCount);
        stats.put("userGroupId", userGroupId);
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, stats));
    }
    
}