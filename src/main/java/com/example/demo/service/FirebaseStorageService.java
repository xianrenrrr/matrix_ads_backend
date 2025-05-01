package com.example.demo.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class FirebaseStorageService {
    private final Storage storage;
    private final String bucketName;

    public FirebaseStorageService() {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucketName = storage.getOptions().getProjectId() + ".appspot.com";
    }

    public static class UploadResult {
        public final String videoUrl;
        public final String thumbnailUrl;
        public UploadResult(String videoUrl, String thumbnailUrl) {
            this.videoUrl = videoUrl;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public UploadResult uploadVideoWithThumbnail(MultipartFile file, String userId, String videoId) throws IOException, InterruptedException {
        // Save video to temp file
        java.io.File tempVideo = java.io.File.createTempFile("upload-", ".mp4");
        file.transferTo(tempVideo);
        // Upload video
        String objectName = String.format("videos/%s/%s/%s", userId, videoId, file.getOriginalFilename());
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(file.getContentType())
                .build();
        storage.create(blobInfo, file.getBytes());
        String videoUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, objectName);
        // Extract thumbnail using FFmpeg
        String thumbName = String.format("videos/%s/%s/thumbnail.jpg", userId, videoId);
        java.io.File tempThumb = java.io.File.createTempFile("thumb-", ".jpg");
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y", "-ss", "1", "-i", tempVideo.getAbsolutePath(), "-frames:v", "1", tempThumb.getAbsolutePath()
        );
        Process proc = pb.start();
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            tempVideo.delete();
            tempThumb.delete();
            throw new IOException("Failed to extract thumbnail with FFmpeg");
        }
        // Upload thumbnail
        BlobInfo thumbBlob = BlobInfo.newBuilder(bucketName, thumbName)
                .setContentType("image/jpeg")
                .build();
        storage.create(thumbBlob, java.nio.file.Files.readAllBytes(tempThumb.toPath()));
        String thumbnailUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, thumbName);
        // Clean up
        tempVideo.delete();
        tempThumb.delete();
        // Log
        System.out.println("[2025-05-01] Uploaded video to: " + videoUrl);
        System.out.println("[2025-05-01] Uploaded thumbnail to: " + thumbnailUrl);
        return new UploadResult(videoUrl, thumbnailUrl);
    }
}
