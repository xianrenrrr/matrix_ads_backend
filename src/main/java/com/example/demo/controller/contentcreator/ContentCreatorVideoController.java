package com.example.demo.controller.contentcreator;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.StorageClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-creator/videos")
public class ContentCreatorVideoController {
    @Autowired
    private Firestore db;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadContentCreatorVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") String templateId,
            @RequestParam("userId") String userId
    ) {
        if (file == null || file.isEmpty() || !StringUtils.hasText(templateId) || !StringUtils.hasText(userId)) {
            return ResponseEntity.badRequest().body("Missing required parameters.");
        }
        try {
            // Upload new video to GCS
            String newVideoId = UUID.randomUUID().toString();
            String newObjectName = String.format("content-creator-videos/%s/%s/%s.mp4", userId, templateId, newVideoId);
            StorageClient.getInstance().bucket().create(newObjectName, file.getInputStream(), file.getContentType());
            String videoUrl = String.format("https://storage.googleapis.com/%s/%s", StorageClient.getInstance().bucket().getName(), newObjectName);

            // Store metadata in submittedVideos/{videoId}
            Map<String, Object> videoMeta = new HashMap<>();
            videoMeta.put("videoId", newVideoId);
            videoMeta.put("templateId", templateId);
            videoMeta.put("uploadedBy", userId);
            videoMeta.put("videoUrl", videoUrl);
            videoMeta.put("thumbnailUrl", null); // Add thumbnail logic if needed
            videoMeta.put("similarityScore", null);
            videoMeta.put("feedback", new ArrayList<>());
            videoMeta.put("publishStatus", "pending");
            videoMeta.put("requestedApproval", true);
            videoMeta.put("submittedAt", FieldValue.serverTimestamp());
            db.collection("submittedVideos").document(newVideoId).set(videoMeta);

            // Update the template's submittedVideos field (map of userId: videoId)
            DocumentReference templateDoc = db.collection("templates").document(templateId);
            templateDoc.update("submittedVideos." + userId, newVideoId);

            // Optionally update user's subscribedTemplates
            DocumentReference userDoc = db.collection("users").document(userId);
            userDoc.update("subscribedTemplates." + templateId, true);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Video uploaded and processed.");
            response.put("videoId", newVideoId);
            response.put("videoUrl", videoUrl);
            response.put("similarityScore", null);
            response.put("feedback", new ArrayList<>());
            response.put("publishStatus", "pending");
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload/process video: " + e.getMessage());
        }
    }

    @GetMapping("/submission")
    public ResponseEntity<?> getContentCreatorVideoSubmission(
            @RequestParam("templateId") String templateId,
            @RequestParam("userId") String userId
    ) {
        if (!StringUtils.hasText(templateId) || !StringUtils.hasText(userId)) {
            return ResponseEntity.badRequest().body("Missing required parameters.");
        }
        try {
            // Query submittedVideos for a doc where templateId and uploadedBy match
            CollectionReference submittedVideosCol = db.collection("submittedVideos");
            Query query = submittedVideosCol.whereEqualTo("templateId", templateId).whereEqualTo("uploadedBy", userId).limit(1);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
            if (!documents.isEmpty()) {
                DocumentSnapshot doc = documents.get(0);
                Map<String, Object> response = new HashMap<>();
                response.put("videoId", doc.getString("videoId"));
                response.put("videoUrl", doc.getString("videoUrl"));
                response.put("similarityScore", doc.contains("similarityScore") ? doc.get("similarityScore") : null);
                response.put("feedback", doc.contains("feedback") ? doc.get("feedback") : new ArrayList<>());
                response.put("publishStatus", doc.contains("publishStatus") ? doc.get("publishStatus") : "pending");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("videoId", null);
                return ResponseEntity.ok(response);
            }
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to fetch submission: " + e.getMessage());
        }
    }
}
