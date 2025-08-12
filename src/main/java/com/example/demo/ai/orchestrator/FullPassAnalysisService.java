package com.example.demo.ai.orchestrator;

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

/**
 * Service for performing full-pass video analysis with all features enabled.
 * Used for short videos where a single comprehensive call is more efficient.
 */
@Service
public class FullPassAnalysisService {
    
    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;
    
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
    
    /**
     * Performs a comprehensive single-pass analysis with all features enabled.
     * This includes SHOT_CHANGE_DETECTION, PERSON_DETECTION, OBJECT_TRACKING, and optionally LABEL_DETECTION.
     * 
     * @param gcsUri GCS URI of the video
     * @return List of scene segments with complete analysis including object overlays
     */
    public List<SceneSegment> performFullPassAnalysis(String gcsUri) {
        System.out.printf("FullPassAnalysisService: Starting comprehensive analysis for %s%n", gcsUri);
        
        try {
            // Get credentials and create client
            GoogleCredentials credentials = firebaseCredentialsUtil.getCredentials();
            VideoIntelligenceServiceSettings settings = VideoIntelligenceServiceSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
            
            try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create(settings)) {
                // Build comprehensive request with all features
                AnnotateVideoRequest.Builder requestBuilder = AnnotateVideoRequest.newBuilder()
                    .setInputUri(gcsUri)
                    .addFeatures(Feature.SHOT_CHANGE_DETECTION)
                    .addFeatures(Feature.PERSON_DETECTION)
                    .addFeatures(Feature.OBJECT_TRACKING);
                
                // Conditionally add label detection
                if (includeLabelDetection) {
                    requestBuilder.addFeatures(Feature.LABEL_DETECTION);
                }
                
                AnnotateVideoRequest request = requestBuilder.build();
                
                System.out.println("FullPassAnalysisService: Sending comprehensive VI request");
                AnnotateVideoResponse response = client.annotateVideoAsync(request).get();
                
                return processFullPassResults(response);
                
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            System.err.printf("FullPassAnalysisService: Error in full pass analysis for %s: %s%n", 
                            gcsUri, e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Processes the comprehensive results from VI API to create scene segments with object overlays
     */
    private List<SceneSegment> processFullPassResults(AnnotateVideoResponse response) {
        List<SceneSegment> sceneSegments = new ArrayList<>();
        
        for (VideoAnnotationResults annotationResult : response.getAnnotationResultsList()) {
            // Process shots with full feature integration
            for (VideoSegment shot : annotationResult.getShotAnnotationsList()) {
                SceneSegment segment = new SceneSegment();
                
                // Set timing
                segment.setStartTime(Duration.ofSeconds(
                    shot.getStartTimeOffset().getSeconds(),
                    shot.getStartTimeOffset().getNanos()
                ));
                segment.setEndTime(Duration.ofSeconds(
                    shot.getEndTimeOffset().getSeconds(),
                    shot.getEndTimeOffset().getNanos()
                ));
                
                // Extract labels (if enabled)
                if (includeLabelDetection) {
                    List<String> sceneLabels = extractLabelsForShot(shot, annotationResult.getShotLabelAnnotationsList());
                    segment.setLabels(sceneLabels);
                }
                
                // Check for person presence
                boolean personPresent = checkPersonPresence(shot, annotationResult.getPersonDetectionAnnotationsList());
                segment.setPersonPresent(personPresent);
                
                // Extract object overlays with full processing pipeline
                List<ObjectOverlay> overlays = extractObjectOverlaysForShot(shot, annotationResult.getObjectAnnotationsList());
                if (!overlays.isEmpty()) {
                    segment.setOverlayObjects(overlays);
                }
                
                sceneSegments.add(segment);
            }
        }
        
        System.out.printf("FullPassAnalysisService: Processed %d scenes with full analysis%n", sceneSegments.size());
        return sceneSegments;
    }
    
    private List<String> extractLabelsForShot(VideoSegment shot, List<LabelAnnotation> labelAnnotations) {
        List<String> labels = new ArrayList<>();
        
        for (LabelAnnotation labelAnnotation : labelAnnotations) {
            for (LabelSegment labelSegment : labelAnnotation.getSegmentsList()) {
                if (isTimeOverlap(shot, labelSegment.getSegment())) {
                    labels.add(labelAnnotation.getEntity().getDescription());
                    break;
                }
            }
        }
        
        return labels;
    }
    
    private boolean checkPersonPresence(VideoSegment shot, List<PersonDetectionAnnotation> personAnnotations) {
        for (PersonDetectionAnnotation personAnnotation : personAnnotations) {
            for (Track track : personAnnotation.getTracksList()) {
                if (isTimeOverlap(shot, track.getSegment())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private List<ObjectOverlay> extractObjectOverlaysForShot(VideoSegment shot, List<ObjectTrackingAnnotation> objectAnnotations) {
        if (objectAnnotations == null || objectAnnotations.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Calculate shot midpoint
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
            if (confidence < confidenceThreshold) continue;
            
            // Apply temporal smoothing
            ObjectOverlay smoothedOverlay = extractSmoothedOverlay(objAnnotation, shotStartSec, shotEndSec, shotMidpointSec, label, confidence);
            
            if (smoothedOverlay != null) {
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
                return Float.compare(scoreB, scoreA);
            })
            .limit(maxObjects)
            .collect(Collectors.toList());
    }
    
    // Helper methods (similar to SceneDetectionServiceImpl)
    private boolean isTimeOverlap(VideoSegment segment1, VideoSegment segment2) {
        long start1 = segment1.getStartTimeOffset().getSeconds();
        long end1 = segment1.getEndTimeOffset().getSeconds();
        long start2 = segment2.getStartTimeOffset().getSeconds();
        long end2 = segment2.getEndTimeOffset().getSeconds();
        
        return start1 <= end2 && start2 <= end1;
    }
    
    private ObjectOverlay extractSmoothedOverlay(ObjectTrackingAnnotation objAnnotation,
                                                double shotStartSec, double shotEndSec, double shotMidpointSec,
                                                String label, float confidence) {
        // Find frames for temporal smoothing
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
            
            if (frameTimeSec < shotStartSec || frameTimeSec > shotEndSec) continue;
            
            double centerDiff = Math.abs(frameTimeSec - shotMidpointSec);
            if (centerDiff < minCenterDiff) {
                minCenterDiff = centerDiff;
                centerFrame = frame;
            }
            
            double leftDiff = Math.abs(frameTimeSec - targetLeft);
            if (leftDiff < minLeftDiff) {
                minLeftDiff = leftDiff;
                leftFrame = frame;
            }
            
            double rightDiff = Math.abs(frameTimeSec - targetRight);
            if (rightDiff < minRightDiff) {
                minRightDiff = rightDiff;
                rightFrame = frame;
            }
        }
        
        if (centerFrame == null) return null;
        
        // Collect frames for averaging
        List<ObjectTrackingFrame> framesToAverage = new ArrayList<>();
        framesToAverage.add(centerFrame);
        
        if (leftFrame != null && leftFrame != centerFrame && minLeftDiff < smoothingDelta * 2) {
            framesToAverage.add(leftFrame);
        }
        
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
        
        // Clamp and validate
        float left = Math.max(0, Math.min(1, avgLeft));
        float top = Math.max(0, Math.min(1, avgTop));
        float right = Math.max(0, Math.min(1, avgRight));
        float bottom = Math.max(0, Math.min(1, avgBottom));
        
        float width = right - left;
        float height = bottom - top;
        
        if (width <= 0 || height <= 0 || (width * height) < minArea) {
            return null;
        }
        
        return new ObjectOverlay(label, confidence, left, top, width, height);
    }
    
    private List<ObjectOverlay> applyNMS(List<ObjectOverlay> overlays) {
        if (overlays == null || overlays.size() <= 1) {
            return overlays != null ? new ArrayList<>(overlays) : new ArrayList<>();
        }
        
        List<ObjectOverlay> sorted = overlays.stream()
            .sorted((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()))
            .collect(Collectors.toList());
        
        List<ObjectOverlay> kept = new ArrayList<>();
        
        for (ObjectOverlay candidate : sorted) {
            boolean shouldKeep = true;
            
            for (ObjectOverlay keptBox : kept) {
                if (calculateIoU(candidate, keptBox) > nmsIouThreshold) {
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
    
    private float calculateIoU(ObjectOverlay box1, ObjectOverlay box2) {
        float x1 = Math.max(box1.getX(), box2.getX());
        float y1 = Math.max(box1.getY(), box2.getY());
        float x2 = Math.min(box1.getX() + box1.getWidth(), box2.getX() + box2.getWidth());
        float y2 = Math.min(box1.getY() + box1.getHeight(), box2.getY() + box2.getHeight());
        
        if (x2 <= x1 || y2 <= y1) return 0.0f;
        
        float intersectionArea = (x2 - x1) * (y2 - y1);
        float area1 = box1.getWidth() * box1.getHeight();
        float area2 = box2.getWidth() * box2.getHeight();
        float unionArea = area1 + area2 - intersectionArea;
        
        return unionArea <= 0 ? 0.0f : intersectionArea / unionArea;
    }
}