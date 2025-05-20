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
    @Autowired
    private com.google.cloud.firestore.Firestore db;

    // --- Notification Endpoints ---
    @GetMapping("/users/{userId}/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(@PathVariable String userId) throws Exception {
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userSnap = userRef.get().get();
        if (!userSnap.exists()) return ResponseEntity.notFound().build();
        Map<String, Object> notifs = (Map<String, Object>) userSnap.get("notifications");
        if (notifs == null) notifs = new HashMap<>();
        return ResponseEntity.ok(notifs);
    }

    @PostMapping("/users/{userId}/notifications")
    public ResponseEntity<String> addNotification(@PathVariable String userId, @RequestBody Map<String, Object> notif) throws Exception {
        DocumentReference userRef = db.collection("users").document(userId);
        String notifId = java.util.UUID.randomUUID().toString();
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read", false);
        userRef.update("notifications." + notifId, notif);
        return ResponseEntity.ok("Notification added.");
    }
}
