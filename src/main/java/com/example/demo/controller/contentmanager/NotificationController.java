package com.example.demo.controller.contentmanager;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/content-manager")
public class NotificationController {
    @Autowired(required = false)
    private com.google.cloud.firestore.Firestore db;

    // --- Notification Endpoints ---
    @GetMapping("/users/{userId}/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(@PathVariable String userId) throws Exception {
        if (db == null) {
            // Return empty notifications when Firebase is disabled
            Map<String, Object> notifs = new HashMap<>();
            return ResponseEntity.ok(notifs);
        }
        
        try {
            DocumentReference userRef = db.collection("users").document(userId);
            DocumentSnapshot userSnap = userRef.get().get();
            if (!userSnap.exists()) return ResponseEntity.notFound().build();
            Map<String, Object> notifs = (Map<String, Object>) userSnap.get("notifications");
            if (notifs == null) notifs = new HashMap<>();
            return ResponseEntity.ok(notifs);
        } catch (Exception e) {
            System.err.println("Error fetching notifications for user " + userId + ": " + e.getMessage());
            // Return empty notifications on error to prevent frontend breaking
            Map<String, Object> notifs = new HashMap<>();
            return ResponseEntity.ok(notifs);
        }
    }

}
