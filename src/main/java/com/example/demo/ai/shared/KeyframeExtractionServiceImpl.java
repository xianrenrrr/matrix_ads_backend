package com.example.demo.ai.shared;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Service
public class KeyframeExtractionServiceImpl implements KeyframeExtractionService {
    
    @Value("${firebase.service-account-key}")
    private String serviceAccountKeyPath;

    @Value("${firebase.storage.bucket}")
    private String bucketName;
    
    private static final String KEYFRAMES_FOLDER = "keyframes/";

    @Override
    public String extractKeyframe(String videoUrl, Duration startTime, Duration endTime) {
        System.out.printf("Extracting keyframe from video: %s (start: %s, end: %s)%n", 
                         videoUrl, startTime, endTime);
        
        try {
            // Create credentials from service account key file
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(serviceAccountKeyPath)
            );
            
            // Calculate midpoint timestamp
            Duration midpoint = startTime.plus(endTime.minus(startTime).dividedBy(2));
            double midpointSeconds = midpoint.getSeconds() + midpoint.getNano() / 1_000_000_000.0;
            
            // Extract bucket name and object name from GCS URL
            String objectName = videoUrl.replace("https://storage.googleapis.com/" + bucketName + "/", "");
            
            // Create storage client with credentials
            Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
            Blob videoBlob = storage.get(bucketName, objectName);
            
            if (videoBlob == null || !videoBlob.exists()) {
                throw new IOException("Video not found in Cloud Storage: " + objectName);
            }
            
            // Create temporary files
            Path tempVideoPath = Files.createTempFile("video_", ".mp4");
            Path tempKeyframePath = Files.createTempFile("keyframe_", ".jpg");
            
            try {
                // Download video
                videoBlob.downloadTo(tempVideoPath);
                
                // Build FFmpeg command
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg",
                    "-i", tempVideoPath.toString(),
                    "-ss", String.valueOf(midpointSeconds),
                    "-vframes", "1",
                    "-q:v", "2",
                    "-y",
                    tempKeyframePath.toString()
                );
                
                System.out.printf("Running FFmpeg command: %s%n", String.join(" ", processBuilder.command()));
                
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    throw new RuntimeException("FFmpeg failed with exit code: " + exitCode);
                }
                
                // Upload keyframe to Cloud Storage
                String keyframeObjectName = KEYFRAMES_FOLDER + UUID.randomUUID().toString() + ".jpg";
                byte[] keyframeBytes = Files.readAllBytes(tempKeyframePath);
                
                BlobId blobId = BlobId.of(bucketName, keyframeObjectName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("image/jpeg")
                    .build();
                
                storage.create(blobInfo, keyframeBytes);
                
                String keyframeUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, keyframeObjectName);
                System.out.printf("Keyframe extracted and uploaded: %s%n", keyframeUrl);
                
                return keyframeUrl;
                
            } finally {
                // Clean up temporary files
                Files.deleteIfExists(tempVideoPath);
                Files.deleteIfExists(tempKeyframePath);
            }
            
        } catch (IOException | InterruptedException e) {
            System.err.printf("Error extracting keyframe from video %s: %s%n", videoUrl, e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}