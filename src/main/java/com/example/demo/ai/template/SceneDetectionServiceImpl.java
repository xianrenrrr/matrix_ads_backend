package com.example.demo.ai.template;

import com.example.demo.model.SceneSegment;
import com.example.demo.util.FirebaseCredentialsUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.videointelligence.v1.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class SceneDetectionServiceImpl implements SceneDetectionService {

    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;

    @Value("${firebase.storage.bucket}")
    private String bucketName;

    @Override
    public List<SceneSegment> detectScenes(String videoUrl) {
        System.out.printf("Starting scene detection for video URL: %s%n", videoUrl);
        
        try {
            // Get credentials using utility (environment or file)
            GoogleCredentials credentials = firebaseCredentialsUtil.getCredentials();
            
            // Create client with credentials
            VideoIntelligenceServiceSettings settings = VideoIntelligenceServiceSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
            
            try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create(settings)) {
                // Normalize to a GCS URI for direct processing (faster, no bytes download)
                String gcsUri = toGcsUri(videoUrl, bucketName);

                // Build the request for Video Intelligence API using GCS URI
                AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                    .setInputUri(gcsUri)
                    .addFeatures(Feature.SHOT_CHANGE_DETECTION)
                    .addFeatures(Feature.LABEL_DETECTION)
                    .addFeatures(Feature.PERSON_DETECTION)
                    .build();

                System.out.println("Sending request to Video Intelligence API");
                AnnotateVideoResponse response = client.annotateVideoAsync(request).get();
                
                List<SceneSegment> sceneSegments = new ArrayList<>();

                for (VideoAnnotationResults annotationResult : response.getAnnotationResultsList()) {
                    // Process shot annotations (scene boundaries)
                    for (VideoSegment shot : annotationResult.getShotAnnotationsList()) {
                        SceneSegment segment = new SceneSegment();
                        
                        // Convert protobuf Duration to java.time.Duration
                        segment.setStartTime(Duration.ofSeconds(
                            shot.getStartTimeOffset().getSeconds(),
                            shot.getStartTimeOffset().getNanos()
                        ));
                        segment.setEndTime(Duration.ofSeconds(
                            shot.getEndTimeOffset().getSeconds(),
                            shot.getEndTimeOffset().getNanos()
                        ));

                        // Extract labels for this scene
                        List<String> sceneLabels = new ArrayList<>();
                        for (LabelAnnotation labelAnnotation : annotationResult.getShotLabelAnnotationsList()) {
                            for (LabelSegment labelSegment : labelAnnotation.getSegmentsList()) {
                                VideoSegment labelVideoSegment = labelSegment.getSegment();
                                
                                // Check if label overlaps with current shot
                                if (isTimeOverlap(shot, labelVideoSegment)) {
                                    sceneLabels.add(labelAnnotation.getEntity().getDescription());
                                    break;
                                }
                            }
                        }
                        segment.setLabels(sceneLabels);

                        // Check for person presence in this scene
                        boolean personPresent = false;
                        for (PersonDetectionAnnotation personAnnotation : annotationResult.getPersonDetectionAnnotationsList()) {
                            for (Track track : personAnnotation.getTracksList()) {
                                if (isTimeOverlap(shot, track.getSegment())) {
                                    personPresent = true;
                                    break;
                                }
                            }
                            if (personPresent) break;
                        }
                        segment.setPersonPresent(personPresent);

                        sceneSegments.add(segment);
                    }
                }

                System.out.printf("Detected %d scenes for video: %s%n", sceneSegments.size(), videoUrl);
                return sceneSegments;
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            System.err.printf("Error detecting scenes for video %s: %s%n", videoUrl, e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private String toGcsUri(String input, String defaultBucket) {
        if (input == null || input.isBlank()) return input;
        if (input.startsWith("gs://")) return input;
        String httpsPrefix = "https://storage.googleapis.com/";
        if (input.startsWith(httpsPrefix)) {
            // https://storage.googleapis.com/<bucket>/<object>
            return "gs://" + input.substring(httpsPrefix.length());
        }
        // If caller passed just an object path, attach default bucket
        if (!input.contains("://")) {
            return "gs://" + defaultBucket + "/" + input.replaceFirst("^/", "");
        }
        // Fallback: return as-is (the API may reject non-GCS URIs)
        return input;
    }

    private boolean isTimeOverlap(VideoSegment segment1, VideoSegment segment2) {
        long start1 = segment1.getStartTimeOffset().getSeconds();
        long end1 = segment1.getEndTimeOffset().getSeconds();
        long start2 = segment2.getStartTimeOffset().getSeconds();
        long end2 = segment2.getEndTimeOffset().getSeconds();
        
        return start1 <= end2 && start2 <= end1;
    }

    /*
     * =========================
     * TODO (Post-MVP Enhancements)
     * =========================
     * 1) Use shot-level labels directly (annotationResult.getShotLabelAnnotationsList())
     *    instead of segment-level + overlap, for cleaner label-to-shot mapping.
     *    DONE: Now using shot-level labels in detectScenes method.
     * 2) Switch time overlap and duration math to nanosecond precision to avoid
     *    boundary errors on very short shots.
     * 3) Provide VideoContext with LabelDetectionConfig set to SHOT_MODE and a
     *    stable model (e.g., "builtin/stable") for reproducibility.
     * 4) Add request timeouts/retries (exponential backoff on RESOURCE_EXHAUSTED).
     * 5) (Optional) Keep top-K labels by confidence to reduce noise in UI.
     * 6) (Optional) Count person tracks and surface a simple integer for analytics.
     * 7) Add structured logs: video duration, shot count, VI latency, bucket/region.
     * 8) Prefer regional endpoints matching bucket location to reduce latency.
     */
    // Change Log:
    // - Updated label extraction loop to use shot-level labels (getShotLabelAnnotationsList) instead of segment-level labels.
}