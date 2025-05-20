package com.example.demo.controller.contentcreator;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/content-creator")
public class NotificationControllerCC {
    @Autowired
    private com.google.cloud.firestore.Firestore db;

    // --- Notification Endpoints for Content Creator ---
    @GetMapping("/users/{userId}/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(@PathVariable String userId) throws Exception {
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userSnap = userRef.get().get();
        if (!userSnap.exists()) return ResponseEntity.notFound().build();
        Map<String, Object> notifs = (Map<String, Object>) userSnap.get("notifications");
        if (notifs == null) notifs = new HashMap<>();
        return ResponseEntity.ok(notifs);
    }

    // --- Placeholder Settings Endpoints for Content Creator ---
    @GetMapping("/users/{userId}/settings")
    public ResponseEntity<Map<String, Object>> getSettings(@PathVariable String userId) {
        // Placeholder: return dummy settings
        Map<String, Object> settings = new HashMap<>();
        settings.put("theme", "light");
        settings.put("notificationsEnabled", true);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/users/{userId}/settings")
    public ResponseEntity<String> updateSettings(@PathVariable String userId, @RequestBody Map<String, Object> settings) {
        // Placeholder: simply return success
        return ResponseEntity.ok("Settings updated (placeholder)");
    }
}
