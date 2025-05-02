package com.example.demo.ai;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.Video;
import com.google.cloud.videointelligence.v1.*;
import com.google.protobuf.ByteString;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AITemplateGeneratorImpl implements AITemplateGenerator {

    /**
     * Generate a template based on the video metadata
     * @param video The video for which to generate a template
     * @return A generated ManualTemplate
     */
    @Override
    public ManualTemplate generateTemplate(Video video) {
        try {
            // Initialize Video Intelligence client
            try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create()) {
                // Read video file from Firebase Storage URL
                byte[] videoBytes = Files.readAllBytes(Paths.get(new URL(video.getUrl()).toURI()));
                ByteString inputVideo = ByteString.copyFrom(videoBytes);

                // Configure video intelligence request
                AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                    .setInputContent(inputVideo)
                    .addFeatures(Feature.SHOT_CHANGE_DETECTION)
                    .addFeatures(Feature.LABEL_DETECTION)
                    .addFeatures(Feature.PERSON_DETECTION)
                    .build();

                // Submit request and wait for results
                AnnotateVideoResponse response = client.annotateVideoAsync(request).get();

                // Process video intelligence results
                ManualTemplate template = new ManualTemplate();
                template.setVideoId(video.getId());
                template.setUserId(video.getUserId());
                template.setTemplateTitle(video.getTitle() + " AI Template");

                List<Scene> scenes = new ArrayList<>();
                
                // Extract scenes based on shot changes and labels
                for (VideoAnnotationResults annotationResult : response.getAnnotationResultsList()) {
                    for (VideoSegment shot : annotationResult.getShotAnnotationsList()) {
                        Scene scene = new Scene();
                        scene.setSceneTitle("Scene at " + shot.getStartTimeOffset());

                        // Check for person presence in this shot
                        boolean personPresent = annotationResult.getPersonDetectionAnnotationsList().stream()
                            .flatMap(person -> person.getTracksList().stream())
                            .anyMatch(track ->
                                track.getSegment().getStartTimeOffset().getSeconds() <= shot.getStartTimeOffset().getSeconds() &&
                                track.getSegment().getEndTimeOffset().getSeconds() >= shot.getEndTimeOffset().getSeconds()
                            );
                        scene.setPresenceOfPerson(personPresent);

                        // Add labels for this shot
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
                return template;
            }
        } catch (Exception e) {
            // Log error and return a basic template
            System.err.println("Error generating AI template: " + e.getMessage());
            ManualTemplate basicTemplate = new ManualTemplate();
            basicTemplate.setVideoId(video.getId());
            basicTemplate.setUserId(video.getUserId());
            basicTemplate.setTemplateTitle(video.getTitle() + " Basic Template");
            return basicTemplate;
        }
    }
}
