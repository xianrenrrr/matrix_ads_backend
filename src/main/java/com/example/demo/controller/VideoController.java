package com.example.demo.controller;

import com.example.demo.dao.VideoDao;
import com.example.demo.model.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/videos")
public class VideoController {
    @Autowired
    private VideoDao videoDao;

    @Autowired
    private com.example.demo.service.FirebaseStorageService firebaseStorageService;

    @PostMapping("/upload")
    public ResponseEntity<Video> uploadVideo(@RequestParam("file") MultipartFile file,
                                             @RequestParam("userId") String userId,
                                             @RequestParam(value = "title", required = false) String title,
                                             @RequestParam(value = "description", required = false) String description) {
        try {
            // Generate a unique videoId
            String videoId = java.util.UUID.randomUUID().toString();
            // Upload to Firebase Storage and extract thumbnail
            com.example.demo.service.FirebaseStorageService.UploadResult result = firebaseStorageService.uploadVideoWithThumbnail(file, userId, videoId);
            Video video = new Video();
            video.setId(videoId);
            video.setUserId(userId);
            video.setTitle(title);
            video.setDescription(description);
            video.setUrl(result.videoUrl);
            video.setThumbnailUrl(result.thumbnailUrl);
            Video saved = videoDao.saveVideo(video);
            // Log
            System.out.println("[2025-05-01] Uploaded video and thumbnail for user " + userId + ", videoId " + videoId);

            // TODO: Call AI model to auto-create template based on this video.
            // For now, create an empty template with the title 'AI model out of usage', using userId and videoId as the template's ID.
            try {
                com.example.demo.model.ManualTemplate template = new com.example.demo.model.ManualTemplate();
                template.setUserId(userId);
                template.setId(videoId); // Use videoId as templateId
                template.setTemplateTitle("AI model out of usage");
                // You can set other fields as needed (e.g., empty scenes)
                templateDao.createTemplate(template);
                System.out.println("[2025-05-01] Created placeholder template for videoId/templateId " + videoId);
            } catch (Exception e) {
                System.err.println("[2025-05-01] Failed to create placeholder template for videoId/templateId " + videoId + ": " + e.getMessage());
            }

            // NOTE: If you use UUID.randomUUID() for each video upload, videoId (and thus templateId) will always be unique. If you ever allow manual setting of IDs, you must check for duplicates.

            return ResponseEntity.ok(saved);
        } catch (ExecutionException | InterruptedException | java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Video>> getVideosByUserId(@PathVariable String userId) {
        try {
            List<Video> videos = videoDao.getVideosByUserId(userId);
            return ResponseEntity.ok(videos);
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<Video> getVideoById(@PathVariable String videoId) {
        try {
            Video video = videoDao.getVideoById(videoId);
            if (video != null) {
                return ResponseEntity.ok(video);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
