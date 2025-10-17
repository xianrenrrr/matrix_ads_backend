package com.example.demo.ai.services;

import com.example.demo.ai.shared.GcsFileResolver;
import com.google.cloud.storage.BlobInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;

/**
 * Service to extract video segments using FFmpeg
 */
@Service
public class VideoSegmentService {
    private static final Logger log = LoggerFactory.getLogger(VideoSegmentService.class);
    
    @Autowired(required = false)
    private com.example.demo.service.FirebaseStorageService firebaseStorageService;
    
    @Autowired
    private GcsFileResolver gcsFileResolver;
    
    @Value("${firebase.storage.bucket}")
    private String bucketName;
    
    /**
     * Extract a video segment and upload to storage
     * 
     * @param videoUrl Original video URL
     * @param startTime Start time of segment
     * @param endTime End time of segment
     * @param segmentId Unique ID for this segment
     * @return URL of extracted segment
     */
    public String extractSegment(String videoUrl, Duration startTime, Duration endTime, String segmentId) {
        File tempInput = null;
        File tempOutput = null;
        
        try {
            log.info("Extracting video segment: {} to {} from {}", startTime, endTime, videoUrl);
            
            // Download original video
            GcsFileResolver.ResolvedFile resolvedFile = gcsFileResolver.resolve(videoUrl);
            tempInput = resolvedFile.getPath().toFile();
            
            // Create temp output file
            tempOutput = File.createTempFile("segment_" + segmentId + "_", ".mp4");
            
            // Calculate duration
            double startSeconds = startTime.toMillis() / 1000.0;
            double durationSeconds = (endTime.toMillis() - startTime.toMillis()) / 1000.0;
            
            // Build FFmpeg command to extract segment
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", tempInput.getAbsolutePath(),
                "-ss", String.format("%.3f", startSeconds),
                "-t", String.format("%.3f", durationSeconds),
                "-c", "copy",  // Copy codec (fast, no re-encoding)
                "-y",  // Overwrite output
                tempOutput.getAbsolutePath()
            );
            
            log.info("Running FFmpeg command: {}", String.join(" ", pb.command()));
            
            Process process = pb.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("time=") || line.contains("error")) {
                    log.debug("FFmpeg: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg segment extraction failed with exit code: {}", exitCode);
                return null;
            }
            
            // Upload segment to storage
            if (firebaseStorageService != null) {
                String objectName = "segments/" + segmentId + ".mp4";
                // Upload using storage.create directly
                BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                    .setContentType("video/mp4")
                    .build();
                com.google.cloud.storage.Storage storage = com.google.firebase.cloud.StorageClient
                    .getInstance()
                    .bucket(bucketName)
                    .getStorage();
                storage.create(blobInfo, java.nio.file.Files.readAllBytes(tempOutput.toPath()));
                
                String segmentUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, objectName);
                log.info("✅ Segment uploaded: {}", segmentUrl);
                return segmentUrl;
            } else {
                log.warn("FirebaseStorageService not available, cannot upload segment");
                return null;
            }
            
        } catch (Exception e) {
            log.error("Failed to extract video segment: {}", e.getMessage(), e);
            return null;
        } finally {
            // Cleanup temp files
            if (tempInput != null && tempInput.exists()) {
                tempInput.delete();
            }
            if (tempOutput != null && tempOutput.exists()) {
                tempOutput.delete();
            }
        }
    }
}
