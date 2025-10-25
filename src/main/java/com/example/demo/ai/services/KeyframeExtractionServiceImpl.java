package com.example.demo.ai.services;

import com.example.demo.service.AlibabaOssStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Service
public class KeyframeExtractionServiceImpl implements KeyframeExtractionService {
    
    @Autowired(required = false)
    private AlibabaOssStorageService ossStorageService;

    @Value("${alibaba.oss.bucket-name}")
    private String bucketName;
    
    private static final String KEYFRAMES_FOLDER = "keyframes/";

    @Override
    public String extractKeyframe(String videoUrl, Duration startTime, Duration endTime) {
        System.out.printf("Extracting keyframe from video: %s (start: %s, end: %s)%n", 
                         videoUrl, startTime, endTime);
        
        if (ossStorageService == null) {
            System.err.println("AlibabaOssStorageService not available");
            return null;
        }
        
        try {
            // Use scene start timestamp (default to 0 if null)
            Duration target = startTime != null ? startTime : Duration.ZERO;
            double targetSeconds = target.getSeconds() + target.getNano() / 1_000_000_000.0;
            
            // Generate signed URL for video download (7 days for processing)
            String signedVideoUrl = ossStorageService.generateSignedUrl(videoUrl, 7, java.util.concurrent.TimeUnit.DAYS);
            
            // Create temporary files
            Path tempVideoPath = Files.createTempFile("video_", ".mp4");
            Path tempKeyframePath = Files.createTempFile("keyframe_", ".jpg");
            
            try {
                // Download video from OSS using signed URL
                System.out.printf("Downloading video from OSS: %s%n", videoUrl);
                java.net.URL url = new java.net.URL(signedVideoUrl);
                try (java.io.InputStream in = url.openStream()) {
                    Files.copy(in, tempVideoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                
                // Build FFmpeg command (seek to scene start)
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg",
                    "-i", tempVideoPath.toString(),
                    "-ss", String.valueOf(targetSeconds),
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
                
                // Upload keyframe to OSS with public-read ACL for AI services
                String keyframeObjectName = KEYFRAMES_FOLDER + UUID.randomUUID().toString() + ".jpg";
                
                // Upload using OSS service with public-read ACL
                String keyframeUrl = ossStorageService.uploadFilePublic(
                    new java.io.FileInputStream(tempKeyframePath.toFile()),
                    keyframeObjectName,
                    "image/jpeg"
                );
                
                System.out.printf("✅ Keyframe extracted and uploaded successfully:%n");
                System.out.printf("   Object name: %s%n", keyframeObjectName);
                System.out.printf("   Public URL: %s%n", keyframeUrl);
                System.out.printf("   Generated at: %s%n", java.time.Instant.now());
                
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
