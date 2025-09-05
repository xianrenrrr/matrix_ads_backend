package com.example.demo.controller.contentmanager;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

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
        
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userSnap = userRef.get().get();
        if (!userSnap.exists()) {
            throw new NoSuchElementException("User not found with ID: " + userId);
        }
        Map<String, Object> notifs = (Map<String, Object>) userSnap.get("notifications");
        if (notifs == null) notifs = new HashMap<>();
        return ResponseEntity.ok(notifs);
    }

    // Update notification settings (email + toggles)
    @PutMapping("/users/{userId}/notification-settings")
    public ResponseEntity<Map<String, Object>> updateNotificationSettings(@PathVariable String userId,
                                                                          @RequestBody Map<String, Object> body) throws Exception {
        if (db == null) {
            // Accept but do nothing if Firebase is disabled
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        }

        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userSnap = userRef.get().get();
        if (!userSnap.exists()) {
            throw new NoSuchElementException("User not found with ID: " + userId);
        }

        Map<String, Object> updates = new HashMap<>();
        if (body.containsKey("notificationEmail")) {
            updates.put("notificationEmail", body.get("notificationEmail"));
        }
        if (body.containsKey("emailNotifications")) {
            updates.put("emailNotificationsEnabled", body.get("emailNotifications"));
        }
        if (body.containsKey("inAppNotifications")) {
            updates.put("inAppNotificationsEnabled", body.get("inAppNotifications"));
        }
        if (!updates.isEmpty()) {
            userRef.update(updates).get();
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

}
