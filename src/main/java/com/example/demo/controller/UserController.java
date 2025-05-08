package com.example.demo.controller;

import com.example.demo.model.ManualTemplate;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private Firestore db;

    // Subscribe to a template (Content Creator)
    @PostMapping("/{userId}/subscribe")
    public ResponseEntity<String> subscribeTemplate(@PathVariable String userId, @RequestParam String templateId) {
        try {
            DocumentReference userRef = db.collection("users").document(userId);
            // Use a transaction to check and update
            String alreadySubscribedMsg = "Already subscribed to this template.";
            String successMsg = "Subscribed to template successfully.";
            final boolean[] alreadySubscribed = {false};
            db.runTransaction(transaction -> {
                DocumentSnapshot userSnap = transaction.get(userRef).get();
                List<String> subscribedTemplates = new ArrayList<>();
                if (userSnap.exists() && userSnap.contains("subscribed_template")) {
                    Object raw = userSnap.get("subscribed_template");
                    if (raw instanceof List<?>) {
                        for (Object obj : (List<?>) raw) {
                            if (obj instanceof String) {
                                subscribedTemplates.add((String) obj);
                            }
                        }
                    }
                }
                if (subscribedTemplates.contains(templateId)) {
                    alreadySubscribed[0] = true;
                } else {
                    subscribedTemplates.add(templateId);
                    transaction.update(userRef, "subscribed_template", subscribedTemplates);
                }
                return null;
            }).get();
            if (alreadySubscribed[0]) {
                return ResponseEntity.badRequest().body(alreadySubscribedMsg);
            }
            return ResponseEntity.ok(successMsg);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to subscribe: " + e.getMessage());
        }
    }

    // Get subscribed templates (Content Creator)
    @GetMapping("/{userId}/subscribed-templates")
    public ResponseEntity<List<ManualTemplate>> getSubscribedTemplates(@PathVariable String userId) throws ExecutionException, InterruptedException {
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userSnap = userRef.get().get();
        List<ManualTemplate> templates = new ArrayList<>();
        boolean userFieldUpdated = false;
        if (userSnap.exists() && userSnap.contains("subscribed_template")) {
            Object raw = userSnap.get("subscribed_template");
            List<String> templateIds = new ArrayList<>();
            if (raw instanceof List<?>) {
                for (Object obj : (List<?>) raw) {
                    if (obj instanceof String) {
                        templateIds.add((String) obj);
                    }
                }
            }
            List<String> validTemplateIds = new ArrayList<>();
            for (String templateId : templateIds) {
                DocumentReference templateRef = db.collection("video_template").document(templateId);
                DocumentSnapshot templateSnap = templateRef.get().get();
                if (templateSnap.exists()) {
                    ManualTemplate template = templateSnap.toObject(ManualTemplate.class);
                    templates.add(template);
                    validTemplateIds.add(templateId);
                } else {
                    userFieldUpdated = true;
                }
            }
            // Clean up user's field if any templates were deleted
            if (userFieldUpdated) {
                userRef.update("subscribed_template", validTemplateIds);
            }
        }
        return ResponseEntity.ok(templates);
    }
}
