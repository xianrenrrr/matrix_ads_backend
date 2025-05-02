package com.example.demo.controller;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.VideoDao;
import com.example.demo.model.Video;
import com.example.demo.model.ManualTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.demo.ai.AITemplateGenerator;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/videos")
public class VideoController {
    @Autowired
    private AITemplateGenerator aiTemplateGenerator;

    private ManualTemplate generateAITemplate(Video video, String userId, String title) {
        // Use AI template generator to create template
        return aiTemplateGenerator.generateTemplate(video);
    }
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
                                             @RequestParam(value = "description", required = false) String description,
                                             @RequestParam(value = "templateId", required = false) String templateId) {
        try {
            // Upload to Firebase Storage and extract thumbnail
            com.example.demo.service.FirebaseStorageService.UploadResult result = firebaseStorageService.uploadVideoWithThumbnail(file, userId, null);

            // Create video
            Video video = new Video();
            video.setUserId(userId);
            video.setTitle(title);
            video.setDescription(description);
            video.setUrl(result.videoUrl);
            video.setThumbnailUrl(result.thumbnailUrl);
            Video savedVideo = videoDao.saveVideo(video);

            // Create a default template if no templateId is provided
            // Determine template creation strategy
            try {
                if (templateId == null) {
                    // Always attempt to generate an AI template
                    ManualTemplate aiGeneratedTemplate = generateAITemplate(savedVideo, userId, title);
                    String savedTemplateId = templateDao.createTemplate(aiGeneratedTemplate);
                    savedVideo.setTemplateId(savedTemplateId);
                    videoDao.updateVideo(savedVideo);
                } else {
                    // If templateId is provided, link the existing template
                    savedVideo.setTemplateId(templateId);
                    videoDao.updateVideo(savedVideo);

                    // Update the template with video ID
                    ManualTemplate existingTemplate = templateDao.getTemplate(templateId);
                    if (existingTemplate != null) {
                        existingTemplate.setVideoId(savedVideo.getId());
                        templateDao.updateTemplate(templateId, existingTemplate);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error creating template: " + e.getMessage());
                throw new RuntimeException("Failed to create or update template", e);
            }

            // Log
            System.out.println("[INFO] [uploadVideo] Uploaded video and thumbnail for user " + userId + ", videoId " + savedVideo.getId());

            return ResponseEntity.ok(savedVideo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
