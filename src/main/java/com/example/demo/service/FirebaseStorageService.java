package com.example.demo.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import com.google.firebase.cloud.StorageClient;

@Service
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseStorageService {
    private final Storage storage;
    private final String bucketName;

    public FirebaseStorageService(@Value("${firebase.storage.bucket}") String bucketName) {
        this.storage = StorageClient.getInstance().bucket(bucketName).getStorage();
        this.bucketName = bucketName;
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
    // Stream upload video to Firebase Storage directly from MultipartFile InputStream
        String objectName = String.format("videos/%s/%s/%s", userId, videoId, file.getOriginalFilename());
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
        .setContentType(file.getContentType())
            .build();
        try (InputStream is = file.getInputStream();
            WritableByteChannel writer = storage.writer(blobInfo)) {
            byte[] buffer = new byte[1024];
            int limit;
            while ((limit = is.read(buffer)) >= 0) {
                writer.write(java.nio.ByteBuffer.wrap(buffer, 0, limit));
            }
        }
        String videoUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, objectName);
        // Save video to temp file for FFmpeg thumbnail extraction
        java.io.File tempVideo = java.io.File.createTempFile("upload-", ".mp4");
        file.transferTo(tempVideo);
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

    public void uploadVideo(InputStream inputStream, String fileName, String contentType) throws IOException {
        String objectName = "videos/" + fileName;

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
            .setContentType(contentType)
            .build();

        try (WritableByteChannel channel = storage.writer(blobInfo);
            OutputStream outputStream = Channels.newOutputStream(channel)) {
            inputStream.transferTo(outputStream);
        }
    }
}

