package com.example.demo.ai.template;

import com.example.demo.model.SceneSegment;
import com.example.demo.model.Scene.ObjectOverlay;
import com.example.demo.util.FirebaseCredentialsUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.videointelligence.v1.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class SceneDetectionServiceImpl implements SceneDetectionService {

    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;

    @Value("${firebase.storage.bucket}")
    private String bucketName;
    
    @Value("${ai.overlay.confidenceThreshold:0.6}")
    private float confidenceThreshold;
    
    @Value("${ai.overlay.maxObjects:4}")
    private int maxObjects;
    
    @Value("${ai.overlay.minArea:0.02}")
    private float minArea;
    
    @Value("${ai.overlay.nmsIouThreshold:0.5}")
    private float nmsIouThreshold;
    
    @Value("${ai.overlay.smoothingDelta:0.15}")
    private double smoothingDelta;
    
    @Value("${ai.template.includeLabelDetection:true}")
    private boolean includeLabelDetection;

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
                AnnotateVideoRequest.Builder requestBuilder = AnnotateVideoRequest.newBuilder()
                    .setInputUri(gcsUri)
                    .addFeatures(Feature.SHOT_CHANGE_DETECTION)
                    .addFeatures(Feature.PERSON_DETECTION)
                    .addFeatures(Feature.OBJECT_TRACKING);  // ADD: Object tracking for overlay
                
                // Conditionally add label detection
                if (includeLabelDetection) {
                    requestBuilder.addFeatures(Feature.LABEL_DETECTION);
                }
                
                AnnotateVideoRequest request = requestBuilder.build();

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

                        // Extract labels for this scene (only if label detection is enabled)
                        List<String> sceneLabels = new ArrayList<>();
                        if (includeLabelDetection) {
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
                        
                        // Extract object overlays for this shot
                        List<ObjectOverlay> shotOverlays = extractObjectOverlaysForShot(
                            shot, annotationResult.getObjectAnnotationsList());
                        if (!shotOverlays.isEmpty()) {
                            segment.setOverlayObjects(shotOverlays);
                        }

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

    /**
     * Extract object overlays for a specific shot from object tracking annotations.
     * Finds the frame closest to the shot midpoint and extracts up to 5 high-confidence objects.
     */
    private List<ObjectOverlay> extractObjectOverlaysForShot(VideoSegment shot, 
                                                             List<ObjectTrackingAnnotation> objectAnnotations) {
        if (objectAnnotations == null || objectAnnotations.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Calculate shot midpoint in seconds with nanosecond precision
        double shotStartSec = shot.getStartTimeOffset().getSeconds() + 
                              shot.getStartTimeOffset().getNanos() / 1_000_000_000.0;
        double shotEndSec = shot.getEndTimeOffset().getSeconds() + 
                            shot.getEndTimeOffset().getNanos() / 1_000_000_000.0;
        double shotMidpointSec = (shotStartSec + shotEndSec) / 2.0;
        
        // Group overlays by label for NMS processing
        Map<String, List<ObjectOverlay>> overlaysByLabel = new HashMap<>();
        
        for (ObjectTrackingAnnotation objAnnotation : objectAnnotations) {
            String label = objAnnotation.getEntity().getDescription();
            if (label == null || label.isBlank()) continue;
            
            float confidence = objAnnotation.getConfidence();
            
            // Filter out low confidence detections early
            if (confidence < confidenceThreshold) continue;
            
            // Apply temporal smoothing across 3 frames
            ObjectOverlay smoothedOverlay = extractSmoothedOverlay(
                objAnnotation, shotStartSec, shotEndSec, shotMidpointSec, label, confidence);
            
            if (smoothedOverlay != null) {
                // Add to label group for NMS processing
                overlaysByLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(smoothedOverlay);
            }
        }
        
        // Apply NMS per label and collect results
        List<ObjectOverlay> allOverlays = new ArrayList<>();
        for (List<ObjectOverlay> labelOverlays : overlaysByLabel.values()) {
            allOverlays.addAll(applyNMS(labelOverlays));
        }
        
        // Sort by confidence * area descending, cap to configured max
        return allOverlays.stream()
            .sorted((a, b) -> {
                float scoreA = a.getConfidence() * a.getWidth() * a.getHeight();
                float scoreB = b.getConfidence() * b.getWidth() * b.getHeight();
                return Float.compare(scoreB, scoreA);  // Descending
            })
            .limit(maxObjects)
            .collect(Collectors.toList());
    }
    
    /**
     * Extract and smooth an object overlay using temporal averaging across 3 frames:
     * t-delta, t (midpoint), t+delta. Falls back gracefully if neighboring frames are missing.
     */
    private ObjectOverlay extractSmoothedOverlay(ObjectTrackingAnnotation objAnnotation,
                                                double shotStartSec, double shotEndSec, double shotMidpointSec,
                                                String label, float confidence) {
        
        // Find frames at t-delta, t, t+delta
        ObjectTrackingFrame centerFrame = null;
        ObjectTrackingFrame leftFrame = null;
        ObjectTrackingFrame rightFrame = null;
        
        double targetLeft = shotMidpointSec - smoothingDelta;
        double targetRight = shotMidpointSec + smoothingDelta;
        
        double minCenterDiff = Double.MAX_VALUE;
        double minLeftDiff = Double.MAX_VALUE;
        double minRightDiff = Double.MAX_VALUE;
        
        for (ObjectTrackingFrame frame : objAnnotation.getFramesList()) {
            double frameTimeSec = frame.getTimeOffset().getSeconds() + 
                                 frame.getTimeOffset().getNanos() / 1_000_000_000.0;
            
            // Only consider frames within shot boundaries
            if (frameTimeSec < shotStartSec || frameTimeSec > shotEndSec) continue;
            
            // Check for center frame (closest to midpoint)
            double centerDiff = Math.abs(frameTimeSec - shotMidpointSec);
            if (centerDiff < minCenterDiff) {
                minCenterDiff = centerDiff;
                centerFrame = frame;
            }
            
            // Check for left frame (closest to t-delta)
            double leftDiff = Math.abs(frameTimeSec - targetLeft);
            if (leftDiff < minLeftDiff) {
                minLeftDiff = leftDiff;
                leftFrame = frame;
            }
            
            // Check for right frame (closest to t+delta)
            double rightDiff = Math.abs(frameTimeSec - targetRight);
            if (rightDiff < minRightDiff) {
                minRightDiff = rightDiff;
                rightFrame = frame;
            }
        }
        
        // Must have at least the center frame
        if (centerFrame == null) {
            return null;
        }
        
        // Collect available frames for averaging
        List<ObjectTrackingFrame> framesToAverage = new ArrayList<>();
        framesToAverage.add(centerFrame);
        
        // Add left frame if it's different from center and reasonably close
        if (leftFrame != null && leftFrame != centerFrame && minLeftDiff < smoothingDelta * 2) {
            framesToAverage.add(leftFrame);
        }
        
        // Add right frame if it's different from center and reasonably close
        if (rightFrame != null && rightFrame != centerFrame && minRightDiff < smoothingDelta * 2) {
            framesToAverage.add(rightFrame);
        }
        
        // Average the bounding boxes
        float avgLeft = 0, avgTop = 0, avgRight = 0, avgBottom = 0;
        
        for (ObjectTrackingFrame frame : framesToAverage) {
            NormalizedBoundingBox bbox = frame.getNormalizedBoundingBox();
            avgLeft += bbox.getLeft();
            avgTop += bbox.getTop();
            avgRight += bbox.getRight();
            avgBottom += bbox.getBottom();
        }
        
        int numFrames = framesToAverage.size();
        avgLeft /= numFrames;
        avgTop /= numFrames;
        avgRight /= numFrames;
        avgBottom /= numFrames;
        
        // Clamp to [0,1] and calculate dimensions
        float left = Math.max(0, Math.min(1, avgLeft));
        float top = Math.max(0, Math.min(1, avgTop));
        float right = Math.max(0, Math.min(1, avgRight));
        float bottom = Math.max(0, Math.min(1, avgBottom));
        
        float width = right - left;
        float height = bottom - top;
        
        // Skip degenerate boxes and those below minimum area
        if (width <= 0 || height <= 0 || (width * height) < minArea) {
            return null;
        }
        
        return new ObjectOverlay(label, confidence, left, top, width, height);
    }
    
    /**
     * Apply Non-Maximum Suppression (NMS) to a list of overlays of the same label.
     * Removes boxes that have high IoU overlap with higher confidence boxes.
     */
    private List<ObjectOverlay> applyNMS(List<ObjectOverlay> overlays) {
        if (overlays == null || overlays.size() <= 1) {
            return overlays != null ? new ArrayList<>(overlays) : new ArrayList<>();
        }
        
        // Sort by confidence descending
        List<ObjectOverlay> sorted = overlays.stream()
            .sorted((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()))
            .collect(Collectors.toList());
        
        List<ObjectOverlay> kept = new ArrayList<>();
        
        for (ObjectOverlay candidate : sorted) {
            boolean shouldKeep = true;
            
            // Check IoU against all previously kept boxes
            for (ObjectOverlay kept_box : kept) {
                if (calculateIoU(candidate, kept_box) > nmsIouThreshold) {
                    shouldKeep = false;
                    break;
                }
            }
            
            if (shouldKeep) {
                kept.add(candidate);
            }
        }
        
        return kept;
    }
    
    /**
     * Calculate Intersection over Union (IoU) between two bounding boxes.
     * Both boxes use normalized coordinates [0,1].
     */
    private float calculateIoU(ObjectOverlay box1, ObjectOverlay box2) {
        // Calculate intersection area
        float x1 = Math.max(box1.getX(), box2.getX());
        float y1 = Math.max(box1.getY(), box2.getY());
        float x2 = Math.min(box1.getX() + box1.getWidth(), box2.getX() + box2.getWidth());
        float y2 = Math.min(box1.getY() + box1.getHeight(), box2.getY() + box2.getHeight());
        
        // No intersection if coordinates don't overlap
        if (x2 <= x1 || y2 <= y1) {
            return 0.0f;
        }
        
        float intersectionArea = (x2 - x1) * (y2 - y1);
        
        // Calculate union area
        float area1 = box1.getWidth() * box1.getHeight();
        float area2 = box2.getWidth() * box2.getHeight();
        float unionArea = area1 + area2 - intersectionArea;
        
        // Avoid division by zero
        if (unionArea <= 0) {
            return 0.0f;
        }
        
        return intersectionArea / unionArea;
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
    // - Enabled VI OBJECT_TRACKING in the same annotate request
    // - Derived per-shot object overlays from the frame closest to the shot midpoint
}