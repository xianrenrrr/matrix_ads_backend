package com.example.demo.ai.orchestrator;

import com.example.demo.util.FirebaseCredentialsUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.videointelligence.v1.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Service for extracting video metadata needed for orchestration decisions
 */
@Service
public class VideoMetadataService {
    
    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;
    
    /**
     * Gets the duration of a video in seconds by analyzing it with VI API.
     * Uses a minimal feature set to get just the video duration.
     * 
     * @param gcsUri GCS URI of the video
     * @return Duration in seconds, or -1 if unable to determine
     */
    public int getVideoDurationSeconds(String gcsUri) {
        System.out.printf("VideoMetadataService: Getting duration for %s%n", gcsUri);
        
        try {
            // Get credentials and create client
            GoogleCredentials credentials = firebaseCredentialsUtil.getCredentials();
            VideoIntelligenceServiceSettings settings = VideoIntelligenceServiceSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
            
            try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create(settings)) {
                // Use minimal feature set to get duration info quickly
                AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                    .setInputUri(gcsUri)
                    .addFeatures(Feature.SHOT_CHANGE_DETECTION) // Minimal feature to trigger analysis
                    .build();
                
                System.out.println("VideoMetadataService: Requesting video duration...");
                AnnotateVideoResponse response = client.annotateVideoAsync(request).get();
                
                // Extract duration from the first annotation result
                for (VideoAnnotationResults annotationResult : response.getAnnotationResultsList()) {
                    if (!annotationResult.getShotAnnotationsList().isEmpty()) {
                        // Get the last shot to determine total video duration
                        VideoSegment lastShot = annotationResult.getShotAnnotationsList()
                            .get(annotationResult.getShotAnnotationsCount() - 1);
                        
                        int durationSeconds = (int) lastShot.getEndTimeOffset().getSeconds();
                        System.out.printf("VideoMetadataService: Video duration is %d seconds%n", durationSeconds);
                        return durationSeconds;
                    }
                }
                
                System.out.println("VideoMetadataService: No shots found, unable to determine duration");
                return -1;
                
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            System.err.printf("VideoMetadataService: Error getting duration for %s: %s%n", 
                            gcsUri, e.getMessage());
            return -1;
        }
    }
}