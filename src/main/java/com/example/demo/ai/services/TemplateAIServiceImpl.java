package com.example.demo.ai.services;

import com.example.demo.ai.services.OverlayLegendService;
import com.example.demo.ai.providers.vision.FFmpegSceneDetectionService;
import com.example.demo.ai.services.KeyframeExtractionService;
import com.example.demo.ai.providers.llm.VideoSummaryService;
import com.example.demo.ai.services.AIOrchestrator;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.SceneSegment;
import com.example.demo.model.Video;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class TemplateAIServiceImpl implements TemplateAIService {
    private static final Logger log = LoggerFactory.getLogger(TemplateAIServiceImpl.class);

    @Autowired
    private FFmpegSceneDetectionService sceneDetectionService;
    
    
    @Autowired
    private AIOrchestrator aiOrchestrator;
    
    @Autowired
    private OverlayLegendService overlayLegendService;
    
    @Autowired
    private KeyframeExtractionService keyframeExtractionService;
    
    @Autowired
    private VideoSummaryService videoSummaryService;
    
    @Value("${ai.template.useObjectOverlay:true}")
    private boolean useObjectOverlay;
    
    @Value("${firebase.storage.bucket}")
    private String bucketName;

    @Override
    public ManualTemplate generateTemplate(Video video) {
        return generateTemplate(video, "zh-CN"); // Chinese-first approach
    }
    
    @Override
    public ManualTemplate generateTemplate(Video video, String language) {
        log.info("Starting AI template generation for video ID: {} in language: {}", video.getId(), language);

        try {
            // Step 1: Detect scenes using FFmpeg (Chinese-first workflow)
            log.info("Step 1: Detecting scenes with FFmpeg...");
            
            String videoUrl = video.getUrl();
            
            // Use FFmpeg for scene detection instead of Google Video Intelligence
            List<SceneSegment> sceneSegments = sceneDetectionService.detectScenes(videoUrl);
            
            if (sceneSegments.isEmpty()) {
                log.info("No scenes detected, creating fallback template");
                return createFallbackTemplate(video);
            }

            // Step 2: Process each scene
            log.info("Step 2: Processing {} detected scenes", sceneSegments.size());
            List<Scene> scenes = new ArrayList<>();
            List<String> allSceneLabels = new ArrayList<>();

            for (int i = 0; i < sceneSegments.size(); i++) {
                SceneSegment segment = sceneSegments.get(i);
                log.info("Processing scene {}/{} with language: {}", i + 1, sceneSegments.size(), language);
                
                Scene scene = processScene(segment, i + 1, video.getUrl(), language);
                scenes.add(scene);
                
                // Collect labels for summary (no more block descriptions)
                if (segment.getLabels() != null) {
                    allSceneLabels.addAll(segment.getLabels());
                }
            }

            // Step 3: Create the template
            log.info("Step 3: Creating final template...");
            ManualTemplate template = new ManualTemplate();
            template.setVideoId(video.getId());
            template.setUserId(video.getUserId());
            template.setTemplateTitle(video.getTitle() + " - AI Generated Template");
            template.setScenes(scenes);
            
            // Set some default values (note: these are hardcoded and not AI-generated)
            template.setVideoFormat("1080p 16:9");
            template.setTotalVideoLength(calculateTotalDuration(sceneSegments));
            
            // LOG: These are preset values, not AI-generated
            log.info("=== AI TEMPLATE DEFAULT VALUES (NOT AI-GENERATED) ===");
            log.info("Setting preset values for videoPurpose, tone, etc.");
            
            template.setVideoPurpose("Product demonstration and promotion");
            template.setTone("Professional");
            template.setLightingRequirements("Good natural or artificial lighting");
            template.setBackgroundMusic("Soft instrumental or ambient music");

            // Step 4: Generate summary (optional) - simplified without block descriptions
            log.info("Step 4: Generating video summary...");
            String summary = videoSummaryService.generateSummary(video, allSceneLabels, new HashMap<>(), language);
            log.info("Generated summary: {}", summary);
            
            log.info("AI template generation completed for video ID: {} with {} scenes", 
                             video.getId(), scenes.size());
            return template;

        } catch (Exception e) {
            log.error("Error in AI template generation for video ID {}: {}", video.getId(), e.getMessage(), e);
            return createFallbackTemplate(video);
        }
    }

    private Scene processScene(SceneSegment segment, int sceneNumber, String videoUrl, String language) {
        // Create base scene with clean data
        Scene scene = SceneProcessor.createFromSegment(segment, sceneNumber);
        
        // Extract keyframe if possible
        String keyframeUrl = extractKeyframe(scene, segment, videoUrl);
        if (keyframeUrl != null) {
            scene.setKeyframeUrl(keyframeUrl);
            scene.setExampleFrame(keyframeUrl);
        }
        
        // Process overlays in a clean, separated way using AI orchestrator
        OverlayProcessor overlayProcessor = new OverlayProcessor(
            aiOrchestrator, overlayLegendService);
        overlayProcessor.processOverlays(scene, segment, keyframeUrl);
        
        return scene;
    }
    
    private String extractKeyframe(Scene scene, SceneSegment segment, String videoUrl) {
        var start = segment.getStartTime();
        var end = segment.getEndTime();
        
        if (start == null || end == null || end.compareTo(start) <= 0) {
            return null;
        }
        
        try {
            log.info("Extracting keyframe for scene {}...", scene.getSceneNumber());
            return keyframeExtractionService.extractKeyframe(videoUrl, start, end);
        } catch (Exception e) {
            log.error("Keyframe extraction failed for scene {}: {}", 
                     scene.getSceneNumber(), e.getMessage());
            return null;
        }
    }

    private int calculateTotalDuration(List<SceneSegment> segments) {
        return segments.stream()
            .mapToInt(segment -> (int) segment.getEndTime().minus(segment.getStartTime()).getSeconds())
            .sum();
    }

    private ManualTemplate createFallbackTemplate(Video video) {
        log.info("Creating fallback template due to processing failure");
        
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
// Change Log: Removed block grid services, simplified to use AI object detection only