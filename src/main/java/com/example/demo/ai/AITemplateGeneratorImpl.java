package com.example.demo.ai;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.Video;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.videointelligence.v1.*;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AITemplateGeneratorImpl implements AITemplateGenerator {

    @Override
    public ManualTemplate generateTemplate(Video video) {
        System.out.printf("Starting template generation for video ID: %s%n", video.getId());

        try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create()) {
            System.out.println("Initialized VideoIntelligenceServiceClient");

            String bucketName = "matrix_ads_video"; // ✅ your bucket name
            String objectName = video.getUrl().replace("https://storage.googleapis.com/" + bucketName + "/", "");

            Storage storage = StorageOptions.getDefaultInstance().getService();
            Blob blob = storage.get(bucketName, objectName);

            if (blob == null || !blob.exists()) {
                throw new IOException("Object not found in Cloud Storage: " + objectName);
            }

            byte[] videoBytes = blob.getContent();            
            System.out.printf("Loaded video bytes from URL: %s%n", video.getUrl());

            ByteString inputVideo = ByteString.copyFrom(videoBytes);

            AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                .setInputContent(inputVideo)
                .addFeatures(Feature.SHOT_CHANGE_DETECTION)
                .addFeatures(Feature.LABEL_DETECTION)
                .addFeatures(Feature.PERSON_DETECTION)
                .build();
            System.out.println("Constructed AnnotateVideoRequest");

            AnnotateVideoResponse response = client.annotateVideoAsync(request).get();
            System.out.printf("Received annotation response for video ID: %s%n", video.getId());
            System.out.println("Response: " + response);
            ManualTemplate template = new ManualTemplate();
            template.setVideoId(video.getId());
            template.setUserId(video.getUserId());
            template.setTemplateTitle(video.getTitle() + " AI Template");

            List<Scene> scenes = new ArrayList<>();

            for (VideoAnnotationResults annotationResult : response.getAnnotationResultsList()) {
                for (VideoSegment shot : annotationResult.getShotAnnotationsList()) {
                    Scene scene = new Scene();
                    scene.setSceneTitle("Scene at " + shot.getStartTimeOffset());

                    boolean personPresent = annotationResult.getPersonDetectionAnnotationsList().stream()
                        .flatMap(person -> person.getTracksList().stream())
                        .anyMatch(track ->
                            track.getSegment().getStartTimeOffset().getSeconds() <= shot.getStartTimeOffset().getSeconds() &&
                            track.getSegment().getEndTimeOffset().getSeconds() >= shot.getEndTimeOffset().getSeconds()
                        );
                    scene.setPresenceOfPerson(personPresent);

                    List<String> sceneLabels = annotationResult.getSegmentLabelAnnotationsList().stream()
                        .filter(label -> label.getSegmentsList().stream().anyMatch(segment ->
                            segment.getSegment().getStartTimeOffset().getSeconds() <= shot.getStartTimeOffset().getSeconds() &&
                            segment.getSegment().getEndTimeOffset().getSeconds() >= shot.getEndTimeOffset().getSeconds()
                        ))
                        .map(label -> label.getEntity().getDescription())
                        .toList();

                    scene.setScriptLine(String.join(", ", sceneLabels));
                    scenes.add(scene);
                }
            }

            template.setScenes(scenes);
            System.out.printf("Generated %d scenes for video ID: %s%n", scenes.size(), video.getId());
            return template;

        } catch (Exception e) {
            System.out.printf("Error generating AI template for video ID %s: %s%n", video.getId(), e.getMessage());
            e.printStackTrace();

            ManualTemplate fallbackTemplate = new ManualTemplate();
            fallbackTemplate.setVideoId(video.getId());
            fallbackTemplate.setUserId(video.getUserId());
            fallbackTemplate.setTemplateTitle(video.getTitle() + " Basic Template");
            return fallbackTemplate;
        }
    }
}