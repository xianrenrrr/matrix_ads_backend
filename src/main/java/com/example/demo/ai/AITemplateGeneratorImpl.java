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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AITemplateGeneratorImpl implements AITemplateGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AITemplateGeneratorImpl.class);
    /**
     * Generate a template based on the video metadata
     * @param video The video for which to generate a template
     * @return A generated ManualTemplate
     */
    @Override
    public ManualTemplate generateTemplate(Video video) {
        logger.info("Starting template generation for video ID: {}", video.getId());

        try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create()) {
            logger.debug("Initialized VideoIntelligenceServiceClient");

            byte[] videoBytes = Files.readAllBytes(Paths.get(new URL(video.getUrl()).toURI()));
            logger.debug("Loaded video bytes from URL: {}", video.getUrl());

            ByteString inputVideo = ByteString.copyFrom(videoBytes);

            AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                .setInputContent(inputVideo)
                .addFeatures(Feature.SHOT_CHANGE_DETECTION)
                .addFeatures(Feature.LABEL_DETECTION)
                .addFeatures(Feature.PERSON_DETECTION)
                .build();
            logger.debug("Constructed AnnotateVideoRequest");

            AnnotateVideoResponse response = client.annotateVideoAsync(request).get();
            logger.info("Received annotation response for video ID: {}", video.getId());

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
            logger.info("Generated {} scenes for video ID: {}", scenes.size(), video.getId());
            return template;

        } catch (Exception e) {
            logger.error("Error generating AI template for video ID {}: {}", video.getId(), e.getMessage(), e);

            ManualTemplate fallbackTemplate = new ManualTemplate();
            fallbackTemplate.setVideoId(video.getId());
            fallbackTemplate.setUserId(video.getUserId());
            fallbackTemplate.setTemplateTitle(video.getTitle() + " Basic Template");
            return fallbackTemplate;
        }
    }
}