package com.example.demo.controller.contentcreator;

import com.example.demo.model.ManualTemplate;

import com.example.demo.dao.UserDao;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-creator")
public class UserController {
    @Autowired
    private Firestore db;
    
    @Autowired
    private UserDao userDao;

    // Subscribe to a template (Content Creator)
    @PostMapping("/users/{userId}/subscribe")
    public ResponseEntity<String> subscribeTemplate(@PathVariable String userId, @RequestParam String templateId) {
        System.out.println("[subscribeTemplate] Called with userId=" + userId + ", templateId=" + templateId);
        try {
            // Check if template exists
            DocumentReference templateRef = db.collection("templates").document(templateId);
            DocumentSnapshot templateSnap = templateRef.get().get();
            if (!templateSnap.exists()) {
                System.out.println("[subscribeTemplate] Template with templateId=" + templateId + " does not exist. Subscription aborted.");
                return ResponseEntity.badRequest().body("Template does not exist.");
            }
            DocumentReference userRef = db.collection("users").document(userId);
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
                    System.out.println("[subscribeTemplate] User exists. Current subscribed_template: " + subscribedTemplates);
                } else {
                    System.out.println("[subscribeTemplate] User does not have 'subscribed_template' field or does not exist.");
                }
                if (subscribedTemplates.contains(templateId)) {
                    System.out.println("[subscribeTemplate] User already subscribed to templateId: " + templateId);
                    alreadySubscribed[0] = true;
                } else {
                    subscribedTemplates.add(templateId);
                    transaction.update(userRef, "subscribed_template", subscribedTemplates);
                    System.out.println("[subscribeTemplate] Added templateId " + templateId + " to user's subscriptions.");
                }
                return null;
            }).get();
            if (alreadySubscribed[0]) {
                System.out.println("[subscribeTemplate] Returning: " + alreadySubscribedMsg);
                return ResponseEntity.badRequest().body(alreadySubscribedMsg);
            }
            System.out.println("[subscribeTemplate] Returning: " + successMsg);
            return ResponseEntity.ok(successMsg);
        } catch (Exception e) {
            System.out.println("[subscribeTemplate] ERROR: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to subscribe: " + e.getMessage());
        }
    }

    // Get subscribed templates (Content Creator)
    @GetMapping("/users/{userId}/subscribed-templates")
    public ResponseEntity<List<ManualTemplate>> getSubscribedTemplates(@PathVariable String userId) throws ExecutionException, InterruptedException {
        try {
            System.out.println("DEBUG: Getting subscribed templates for user: " + userId);
            
            // Get user's subscribed templates using UserDao
            Map<String, Boolean> subscribedTemplatesMap = userDao.getSubscribedTemplates(userId);
            System.out.println("DEBUG: User subscribed templates map: " + subscribedTemplatesMap);
            
            List<ManualTemplate> templates = new ArrayList<>();
            
            // Get template details for each subscribed template
            for (String templateId : subscribedTemplatesMap.keySet()) {
                if (Boolean.TRUE.equals(subscribedTemplatesMap.get(templateId))) {
                    try {
                        DocumentReference templateRef = db.collection("templates").document(templateId);
                        DocumentSnapshot templateSnap = templateRef.get().get();
                        if (templateSnap.exists()) {
                            ManualTemplate template = templateSnap.toObject(ManualTemplate.class);
                            if (template != null) {
                                template.setId(templateId); // Ensure ID is set
                                templates.add(template);
                                System.out.println("DEBUG: Added template: " + templateId + " - " + template.getTemplateTitle());
                            }
                        } else {
                            System.out.println("DEBUG: Template not found: " + templateId + " - removing from user subscriptions");
                            // Remove non-existent template from user's subscriptions
                            userDao.removeSubscribedTemplate(userId, templateId);
                        }
                    } catch (Exception e) {
                        System.out.println("DEBUG: Error processing template " + templateId + ": " + e.getMessage());
                    }
                }
            }
            
            System.out.println("DEBUG: Returning " + templates.size() + " templates");
            return ResponseEntity.ok(templates);
            
        } catch (Exception e) {
            System.out.println("DEBUG: Error getting subscribed templates: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // Get all submissions for a content creator user
    @GetMapping("/users/{userId}/submissions")
    public ResponseEntity<List<Map<String, Object>>> getMySubmissions(@PathVariable String userId) throws ExecutionException, InterruptedException {
        CollectionReference submittedVideosRef = db.collection("submittedVideos");
        Query query = submittedVideosRef.whereEqualTo("uploadedBy", userId);
        List<Map<String, Object>> submissions = new ArrayList<>();
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        for (DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
            submissions.add(doc.getData());
        }
        return ResponseEntity.ok(submissions);
    }

        // Inject TemplateDao for template access
        @Autowired
        private com.example.demo.dao.TemplateDao templateDao;
    
        // Get template details (Content Creator)
        @GetMapping("/templates/{templateId}")
        public ResponseEntity<ManualTemplate> getTemplateByIdForContentCreator(@PathVariable String templateId) {
            try {
                ManualTemplate template = templateDao.getTemplate(templateId);
                if (template != null) {
                    return ResponseEntity.ok(template);
                } else {
                    return ResponseEntity.notFound().build();
                }
            } catch (Exception e) {
                return ResponseEntity.internalServerError().build();
            }
        }
}
