package com.example.demo.ai.template;

import com.example.demo.ai.shared.BlockDescriptionService;
import com.example.demo.ai.shared.KeyframeExtractionService;
import com.example.demo.ai.shared.VideoSummaryService;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.SceneSegment;
import com.example.demo.model.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AITemplateGeneratorImpl implements AITemplateGenerator {

    @Autowired
    private SceneDetectionService sceneDetectionService;
    
    @Autowired
    private KeyframeExtractionService keyframeExtractionService;
    
    @Autowired
    private BlockGridService blockGridService;
    
    @Autowired
    private BlockDescriptionService blockDescriptionService;
    
    @Autowired
    private VideoSummaryService videoSummaryService;

    @Override
    public ManualTemplate generateTemplate(Video video) {
        System.out.printf("Starting AI template generation for video ID: %s%n", video.getId());

        try {
            // Step 1: Detect scenes using Google Video Intelligence API
            System.out.println("Step 1: Detecting scenes...");
            List<SceneSegment> sceneSegments = sceneDetectionService.detectScenes(video.getUrl());
            
            if (sceneSegments.isEmpty()) {
                System.out.println("No scenes detected, creating fallback template");
                return createFallbackTemplate(video);
            }

            // Step 2: Process each scene
            System.out.printf("Step 2: Processing %d detected scenes...%n", sceneSegments.size());
            List<Scene> scenes = new ArrayList<>();
            List<String> allSceneLabels = new ArrayList<>();
            Map<String, String> allBlockDescriptions = new HashMap<>();

            for (int i = 0; i < sceneSegments.size(); i++) {
                SceneSegment segment = sceneSegments.get(i);
                System.out.printf("Processing scene %d/%d...%n", i + 1, sceneSegments.size());
                
                Scene scene = processScene(segment, i + 1, video.getUrl());
                scenes.add(scene);
                
                // Collect data for summary
                allSceneLabels.addAll(segment.getLabels());
                if (scene.getBlockDescriptions() != null) {
                    allBlockDescriptions.putAll(scene.getBlockDescriptions());
                }
            }

            // Step 3: Create the template
            System.out.println("Step 3: Creating final template...");
            ManualTemplate template = new ManualTemplate();
            template.setVideoId(video.getId());
            template.setUserId(video.getUserId());
            template.setTemplateTitle(video.getTitle() + " - AI Generated Template");
            template.setScenes(scenes);
            
            // Set some default values
            template.setVideoFormat("1080p 16:9");
            template.setTotalVideoLength(calculateTotalDuration(sceneSegments));
            template.setVideoPurpose("Product demonstration and promotion");
            template.setTone("Professional");
            template.setLightingRequirements("Good natural or artificial lighting");
            template.setBackgroundMusic("Soft instrumental or ambient music");

            // Step 4: Generate summary (optional)
            System.out.println("Step 4: Generating video summary...");
            String summary = videoSummaryService.generateSummary(video, allSceneLabels, allBlockDescriptions);
            System.out.printf("Generated summary: %s%n", summary);
            
            System.out.printf("AI template generation completed for video ID: %s with %d scenes%n", 
                             video.getId(), scenes.size());
            return template;

        } catch (Exception e) {
            System.err.printf("Error in AI template generation for video ID %s: %s%n", video.getId(), e.getMessage());
            e.printStackTrace();
            return createFallbackTemplate(video);
        }
    }

    private Scene processScene(SceneSegment segment, int sceneNumber, String videoUrl) {
        Scene scene = new Scene();
        scene.setSceneNumber(sceneNumber);
        scene.setSceneTitle(String.format("Scene %d", sceneNumber));
        scene.setStartTime(segment.getStartTime());
        scene.setEndTime(segment.getEndTime());
        scene.setSceneDurationInSeconds(segment.getEndTime().minus(segment.getStartTime()).getSeconds());
        scene.setPresenceOfPerson(segment.isPersonPresent());
        scene.setScriptLine(String.join(", ", segment.getLabels()));

        try {
            // Step 2a: Extract keyframe at scene midpoint
            System.out.printf("Extracting keyframe for scene %d...%n", sceneNumber);
            String keyframeUrl = keyframeExtractionService.extractKeyframe(
                videoUrl, segment.getStartTime(), segment.getEndTime()
            );
            
            if (keyframeUrl != null) {
                scene.setKeyframeUrl(keyframeUrl);
                scene.setExampleFrame(keyframeUrl); // Also set the existing field

                // Step 2b: Create 3x3 block grid from keyframe
                System.out.printf("Creating block grid for scene %d...%n", sceneNumber);
                Map<String, String> blockImageUrls = blockGridService.createBlockGrid(keyframeUrl);
                scene.setBlockImageUrls(blockImageUrls);

                if (!blockImageUrls.isEmpty()) {
                    // Step 2c: Describe each block using GPT-4o
                    System.out.printf("Describing blocks for scene %d...%n", sceneNumber);
                    Map<String, String> blockDescriptions = blockDescriptionService.describeBlocks(blockImageUrls);
                    scene.setBlockDescriptions(blockDescriptions);

                    // Set screen grid overlay based on described blocks
                    scene.setScreenGridOverlay(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9)); // All 9 blocks
                    scene.setScreenGridOverlayLabels(new ArrayList<>(blockDescriptions.values()));
                }
            }

            // Set some intelligent defaults based on analysis
            scene.setDeviceOrientation("Phone (Portrait 9:16)");
            scene.setPersonPosition(segment.isPersonPresent() ? "Center" : "No Preference");
            scene.setPreferredGender("No Preference");
            scene.setMovementInstructions("Static");
            scene.setBackgroundInstructions("Use similar background as shown in example frame");
            scene.setSpecificCameraInstructions("Follow the framing shown in the example");
            scene.setAudioNotes("Clear speech, match the tone of the scene");

        } catch (Exception e) {
            System.err.printf("Error processing scene %d: %s%n", sceneNumber, e.getMessage());
            // Continue with basic scene info even if advanced processing fails
        }

        return scene;
    }

    private int calculateTotalDuration(List<SceneSegment> segments) {
        return segments.stream()
            .mapToInt(segment -> (int) segment.getEndTime().minus(segment.getStartTime()).getSeconds())
            .sum();
    }

    private ManualTemplate createFallbackTemplate(Video video) {
        System.out.println("Creating fallback template due to processing failure");
        
        ManualTemplate template = new ManualTemplate();
        template.setVideoId(video.getId());
        template.setUserId(video.getUserId());
        template.setTemplateTitle(video.getTitle() + " - Basic Template");
        template.setVideoFormat("1080p 16:9");
        template.setTotalVideoLength(30); // Default 30 seconds
        template.setVideoPurpose("Basic video content showcase");
        template.setTone("Professional");
        template.setLightingRequirements("Good lighting required");
        template.setBackgroundMusic("Light background music recommended");

        // Create a single default scene
        Scene defaultScene = new Scene();
        defaultScene.setSceneNumber(1);
        defaultScene.setSceneTitle("Main Scene");
        defaultScene.setSceneDurationInSeconds(30);
        defaultScene.setScriptLine("Please record your content following the template guidelines");
        defaultScene.setPresenceOfPerson(true);
        defaultScene.setPreferredGender("No Preference");
        defaultScene.setPersonPosition("Center");
        defaultScene.setDeviceOrientation("Phone (Portrait 9:16)");
        defaultScene.setMovementInstructions("Static");
        defaultScene.setBackgroundInstructions("Use a clean, professional background");
        defaultScene.setSpecificCameraInstructions("Frame yourself from chest up, looking directly at camera");
        defaultScene.setAudioNotes("Speak clearly and at moderate pace");

        template.setScenes(List.of(defaultScene));
        return template;
    }
}