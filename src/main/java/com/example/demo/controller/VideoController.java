package com.example.demo.controller;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.VideoDao;
import com.example.demo.model.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/videos")
public class VideoController {
    @Autowired
    private VideoDao videoDao;

    @Autowired
    private TemplateDao templateDao; // Add this field

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
            System.out.println("[INFO] [uploadVideo] Generated videoId: " + videoId);
        com.example.demo.service.FirebaseStorageService.UploadResult result = firebaseStorageService.uploadVideoWithThumbnail(file, userId, videoId);
        System.out.println("[INFO] [uploadVideo] Uploaded to Firebase. videoUrl=" + result.videoUrl + ", thumbnailUrl=" + result.thumbnailUrl);
            Video video = new Video();
            video.setId(videoId);
            video.setUserId(userId);
            video.setTitle(title);
            video.setDescription(description);
            video.setUrl(result.videoUrl);
            video.setThumbnailUrl(result.thumbnailUrl);
            System.out.println("[INFO] [uploadVideo] Saving video metadata to DB for videoId: " + videoId);
            Video saved = videoDao.saveVideo(video);
            System.out.println("[INFO] [uploadVideo] Saved video metadata to DB for videoId: " + videoId);
            // Log
            System.out.println("[INFO] [uploadVideo] Uploaded video and thumbnail for user " + userId + ", videoId " + videoId);

            // TODO: Call AI model to auto-create template based on this video.
            // For now, create an empty template with the title 'AI model out of usage', using userId and videoId as the template's ID.
            try {
                System.out.println("[INFO] [uploadVideo] Creating placeholder template for videoId/templateId " + videoId);
                com.example.demo.model.ManualTemplate template = new com.example.demo.model.ManualTemplate();
                template.setUserId(userId);
                template.setId(videoId); // Use videoId as templateId
                template.setTemplateTitle("AI model out of usage");
                // You can set other fields as needed (e.g., empty scenes)
                templateDao.createTemplate(template);
                System.out.println("[INFO] [uploadVideo] Created placeholder template for videoId/templateId " + videoId);
            } catch (Exception e) {
                System.err.println("[ERROR] [uploadVideo] Failed to create placeholder template for videoId/templateId " + videoId + ": " + e.getMessage());
                e.printStackTrace();
            }

            // NOTE: If you use UUID.randomUUID() for each video upload, videoId (and thus templateId) will always be unique. If you ever allow manual setting of IDs, you must check for duplicates.

            return ResponseEntity.ok(saved);
        } catch (ExecutionException | InterruptedException | java.io.IOException e) {
            System.err.println("[ERROR] [uploadVideo] Exception during upload: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
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
