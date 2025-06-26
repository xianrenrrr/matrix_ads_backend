package com.example.demo.ai;

import com.example.demo.model.SceneSegment;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.videointelligence.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class SceneDetectionServiceImpl implements SceneDetectionService {

    @Override
    public List<SceneSegment> detectScenes(String videoUrl) {
        System.out.printf("Starting scene detection for video URL: %s%n", videoUrl);
        
        try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create()) {
            // Extract bucket name and object name from GCS URL
            String bucketName = "matrix_ads_video";
            String objectName = videoUrl.replace("https://storage.googleapis.com/" + bucketName + "/", "");

            // Load video from Google Cloud Storage
            Storage storage = StorageOptions.getDefaultInstance().getService();
            Blob blob = storage.get(bucketName, objectName);

            if (blob == null || !blob.exists()) {
                throw new IOException("Video not found in Cloud Storage: " + objectName);
            }

            byte[] videoBytes = blob.getContent();
            ByteString inputVideo = ByteString.copyFrom(videoBytes);

            // Build the request for Video Intelligence API
            AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                .setInputContent(inputVideo)
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
                    for (LabelAnnotation labelAnnotation : annotationResult.getSegmentLabelAnnotationsList()) {
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

        } catch (IOException | ExecutionException | InterruptedException e) {
            System.err.printf("Error detecting scenes for video %s: %s%n", videoUrl, e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private boolean isTimeOverlap(VideoSegment segment1, VideoSegment segment2) {
        long start1 = segment1.getStartTimeOffset().getSeconds();
        long end1 = segment1.getEndTimeOffset().getSeconds();
        long start2 = segment2.getStartTimeOffset().getSeconds();
        long end2 = segment2.getEndTimeOffset().getSeconds();
        
        return start1 <= end2 && start2 <= end1;
    }
}