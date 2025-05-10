package com.example.demo.controller;

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
            // 1. Delete old video (if any)
            CollectionReference submittedVideosCol = db.collection("video_template").document(templateId)
                .collection("submittedVideos");
            DocumentReference userVideoDocRef = submittedVideosCol.document(userId);
            DocumentSnapshot userVideoSnap = userVideoDocRef.get().get();
            String oldVideoId = null;
            String oldVideoUrl = null;
            if (userVideoSnap.exists() && userVideoSnap.contains("videoId")) {
                oldVideoId = userVideoSnap.getString("videoId");
                oldVideoUrl = userVideoSnap.getString("videoUrl");
            }
            if (oldVideoId != null && oldVideoUrl != null) {
                // Delete Firestore doc
                userVideoDocRef.delete();
                // Delete GCS file
                String bucketName = StorageClient.getInstance().bucket().getName();
                String objectName = String.format("content-creator-videos/%s/%s/%s.mp4", userId, templateId, oldVideoId);
                StorageClient.getInstance().bucket().getStorage().delete(bucketName, objectName);
            }

            // 2. Upload new video to GCS
            String newVideoId = UUID.randomUUID().toString();
            String newObjectName = String.format("content-creator-videos/%s/%s/%s.mp4", userId, templateId, newVideoId);
            StorageClient.getInstance().bucket().create(newObjectName, file.getInputStream(), file.getContentType());
            String videoUrl = String.format("https://storage.googleapis.com/%s/%s", StorageClient.getInstance().bucket().getName(), newObjectName);

            // 3. Store metadata in Firestore
            Map<String, Object> videoMeta = new HashMap<>();
            videoMeta.put("videoId", newVideoId);
            videoMeta.put("videoUrl", videoUrl);
            videoMeta.put("createdAt", FieldValue.serverTimestamp());
            videoMeta.put("published", false);
            videoMeta.put("similarityScore", null);
            videoMeta.put("feedback", new ArrayList<>());
            userVideoDocRef.set(videoMeta);

            // 4. [Placeholder for AI Check]
            // TODO: Implement similarity check and feedback in future

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Video uploaded and processed.");
            response.put("videoId", newVideoId);
            response.put("videoUrl", videoUrl);
            response.put("similarityScore", null);
            response.put("feedback", new ArrayList<>());
            response.put("publishStatus", "pending");
            return ResponseEntity.ok(response);
        } catch (IOException | InterruptedException | ExecutionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload/process video: " + e.getMessage());
        }
    }
}
