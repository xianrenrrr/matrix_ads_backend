package com.example.demo.ai.template;

import com.example.demo.ai.guidance.OverlayLegendService;
import com.example.demo.ai.orchestrator.VideoAnalysisOrchestrator;
import com.example.demo.ai.shared.KeyframeExtractionService;
import com.example.demo.ai.shared.VideoSummaryService;
import com.example.demo.ai.translate.TranslationService;
import com.example.demo.ai.guidance.OverlayLegendService;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.SceneSegment;
import com.example.demo.model.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AITemplateGeneratorImpl implements AITemplateGenerator {

    @Autowired
    private VideoAnalysisOrchestrator videoAnalysisOrchestrator;
    
    @Autowired
    private TranslationService translationService;
    
    @Autowired
    private ObjectLocalizationService objectLocalizationService;
    
    @Autowired
    private OverlayLegendService overlayLegendService;
    
    @Autowired
    private KeyframeExtractionService keyframeExtractionService;
    
    @Autowired
    private VideoSummaryService videoSummaryService;
    
    @Autowired
    private OverlayLegendService overlayLegendService;
    
    @Value("${ai.template.useObjectOverlay:true}")
    private boolean useObjectOverlay;
    
    @Value("${firebase.storage.bucket}")
    private String bucketName;

    @Override
    public ManualTemplate generateTemplate(Video video) {
        return generateTemplate(video, "en");
    }
    
    @Override
    public ManualTemplate generateTemplate(Video video, String language) {
        System.out.printf("Starting AI template generation for video ID: %s in language: %s%n", video.getId(), language);

        try {
            // Step 1: Analyze video using orchestrator (multi-pass approach)
            System.out.println("Step 1: Analyzing video with orchestrator...");
            
            // Convert video URL to GCS URI if needed
            String videoUrl = video.getUrl();
            String gcsUri = toGcsUri(videoUrl);
            
            // Use orchestrator for centralized VI calls
            List<SceneSegment> sceneSegments = videoAnalysisOrchestrator.analyze(gcsUri);
            
            if (sceneSegments.isEmpty()) {
                System.out.println("No scenes detected, creating fallback template");
                return createFallbackTemplate(video);
            }

            // Step 2: Process each scene
            System.out.printf("Step 2: Processing %d detected scenes...%n", sceneSegments.size());
            List<Scene> scenes = new ArrayList<>();
            List<String> allSceneLabels = new ArrayList<>();

            for (int i = 0; i < sceneSegments.size(); i++) {
                SceneSegment segment = sceneSegments.get(i);
                System.out.printf("Processing scene %d/%d with language: %s%n", i + 1, sceneSegments.size(), language);
                
                Scene scene = processScene(segment, i + 1, video.getUrl(), language);
                scenes.add(scene);
                
                // Collect labels for summary (no more block descriptions)
                if (segment.getLabels() != null) {
                    allSceneLabels.addAll(segment.getLabels());
                }
            }

            // Step 3: Create the template
            System.out.println("Step 3: Creating final template...");
            ManualTemplate template = new ManualTemplate();
            template.setVideoId(video.getId());
            template.setUserId(video.getUserId());
            template.setTemplateTitle(video.getTitle() + " - AI Generated Template");
            template.setScenes(scenes);
            
            // Set some default values (note: these are hardcoded and not AI-generated)
            template.setVideoFormat("1080p 16:9");
            template.setTotalVideoLength(calculateTotalDuration(sceneSegments));
            
            // LOG: These are preset values, not AI-generated
            System.out.println("=== AI TEMPLATE DEFAULT VALUES (NOT AI-GENERATED) ===");
            System.out.println("Setting preset values for videoPurpose, tone, etc.");
            
            template.setVideoPurpose("Product demonstration and promotion");
            template.setTone("Professional");
            template.setLightingRequirements("Good natural or artificial lighting");
            template.setBackgroundMusic("Soft instrumental or ambient music");

            // Step 4: Generate summary (optional) - simplified without block descriptions
            System.out.println("Step 4: Generating video summary...");
            String summary = videoSummaryService.generateSummary(video, allSceneLabels, new HashMap<>(), language);
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

    private Scene processScene(SceneSegment segment, int sceneNumber, String videoUrl, String language) {
        Scene scene = new Scene();
        scene.setSceneNumber(sceneNumber);
        scene.setSceneTitle("Scene " + sceneNumber);
        
        // Mark as AI-generated scene
        scene.setSceneSource("ai");

        // Cache start/end and compute duration safely
        var start = segment.getStartTime();
        var end = segment.getEndTime();
        scene.setStartTime(start);
        scene.setEndTime(end);
        long durationSec = 0L;
        if (start != null && end != null) {
            try {
                durationSec = Math.max(0L, end.minus(start).getSeconds());
            } catch (Exception ignored) {
                durationSec = 0L;
            }
        }
        scene.setSceneDurationInSeconds(durationSec);

        // Person/labels (null-safe)
        scene.setPresenceOfPerson(segment.isPersonPresent());
        var labels = segment.getLabels();
        scene.setScriptLine(labels == null || labels.isEmpty() ? "" : String.join(", ", labels));
        
        // AI scenes now always use object overlay detection from our orchestrator
        boolean hasObjects = segment.getOverlayObjects() != null && !segment.getOverlayObjects().isEmpty();
        
        if (hasObjects) {
            // Use object overlay mode with AI-detected objects
            System.out.printf("Scene %d: Using AI object overlay with %d objects%n", 
                            sceneNumber, segment.getOverlayObjects().size());
            scene.setOverlayType("objects");
            scene.setOverlayObjects(segment.getOverlayObjects());
            
            // Translate labels for overlay objects
            if (translationService != null && scene.getOverlayObjects() != null && !scene.getOverlayObjects().isEmpty()) {
                var overlayObjects = scene.getOverlayObjects();
                List<String> labelsToTranslate = overlayObjects.stream()
                    .map(obj -> obj.getLabel())
                    .filter(label -> label != null && !label.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!labelsToTranslate.isEmpty()) {
                    // Default to zh-CN for Chinese localization
                    String targetLocale = (language != null && language.contains("zh")) ? language : "zh-CN";
                    Map<String, String> translations = translationService.translateLabels(labelsToTranslate, targetLocale);
                    
                    // Apply translations to overlay objects
                    for (var overlay : overlayObjects) {
                        String translated = translations.get(overlay.getLabel());
                        if (translated != null) {
                            overlay.setLabelLocalized(translated);
                        }
                    }
                    
                    System.out.printf("Scene %d: Translated %d labels to %s%n", 
                        sceneNumber, labelsToTranslate.size(), targetLocale);
                }
            }
            
            // Generate legend for AI scenes with objects
            scene.setLegend(overlayLegendService.buildLegendFromObjects(scene));
            scene.setSourceAspect("9:16");  // MVP: force portrait, OK for your use case
            
            System.out.printf("Scene %d: Generated legend with %d items%n", 
                            sceneNumber, scene.getLegend().size());
        } else {
            // Simple fallback when no objects detected - just keyframe guidance
            System.out.printf("Scene %d: No objects detected, using simple keyframe guidance%n", sceneNumber);
            scene.setOverlayType("grid");
            // Set minimal grid overlay for compatibility
            scene.setScreenGridOverlay(java.util.List.of(5)); // Center grid only
        }
        
        // Extract keyframe for all scenes (for reference display)
        try {
            if (start != null && end != null && end.compareTo(start) > 0) {
                System.out.printf("Extracting keyframe for scene %d...%n", sceneNumber);
                String keyframeUrl = keyframeExtractionService.extractKeyframe(videoUrl, start, end);
                if (keyframeUrl != null && !keyframeUrl.isBlank()) {
                    scene.setKeyframeUrl(keyframeUrl);
                    scene.setExampleFrame(keyframeUrl);
                    
                    // Try to detect polygons from keyframe if enabled
                    if (objectLocalizationService != null) {
                        try {
                            System.out.printf("Detecting polygons from keyframe for scene %d...%n", sceneNumber);
                            var polygons = objectLocalizationService.detectObjectPolygons(keyframeUrl);
                            
                            if (!polygons.isEmpty()) {
                                // Prefer polygons over bounding boxes
                                System.out.printf("Scene %d: Found %d polygon shapes, switching to polygon mode%n", 
                                    sceneNumber, polygons.size());
                                scene.setOverlayType("polygons");
                                scene.setOverlayPolygons(polygons);
                                scene.setOverlayObjects(null); // Clear boxes since we have polygons
                                
                                // Translate polygon labels
                                if (translationService != null) {
                                    List<String> labelsToTranslate = polygons.stream()
                                        .map(p -> p.getLabel())
                                        .filter(label -> label != null && !label.isEmpty())
                                        .distinct()
                                        .collect(Collectors.toList());
                                    
                                    if (!labelsToTranslate.isEmpty()) {
                                        String targetLocale = (language != null && language.contains("zh")) ? language : "zh-CN";
                                        Map<String, String> translations = translationService.translateLabels(labelsToTranslate, targetLocale);
                                        
                                        // Apply translations to polygons
                                        for (var polygon : polygons) {
                                            String translated = translations.get(polygon.getLabel());
                                            if (translated != null) {
                                                polygon.setLabelLocalized(translated);
                                            }
                                        }
                                        
                                        System.out.printf("Scene %d: Translated %d polygon labels to %s%n", 
                                            sceneNumber, labelsToTranslate.size(), targetLocale);
                                    }
                                }
                            } else {
                                System.out.printf("Scene %d: No polygons detected from keyframe%n", sceneNumber);
                            }
                        } catch (Exception e) {
                            System.err.printf("Error detecting polygons for scene %d: %s%n", sceneNumber, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("Error extracting keyframe for scene %d: %s%n", sceneNumber, e.getMessage());
        }

        // Generate legend for overlays (after all overlay processing is complete)
        if (overlayLegendService != null && scene.getOverlayType() != null && !scene.getOverlayType().equals("grid")) {
            String targetLocale = (language != null && language.contains("zh")) ? language : "zh-CN";
            var legend = overlayLegendService.buildLegend(scene, targetLocale);
            scene.setLegend(legend);
            
            if (!legend.isEmpty()) {
                System.out.printf("Scene %d: Generated legend with %d items (first color: %s)%n", 
                    sceneNumber, legend.size(), legend.get(0).getColorHex());
            }
        }
        
        // Set source aspect ratio (default to portrait for MVP)
        scene.setSourceAspect("9:16");

        // Intelligent defaults based on analysis
        scene.setDeviceOrientation("Phone (Portrait 9:16)");
        scene.setPersonPosition(segment.isPersonPresent() ? "Center" : "No Preference");
        scene.setPreferredGender("No Preference");
        scene.setMovementInstructions("Static");
        scene.setBackgroundInstructions("Use similar background as shown in example frame");
        scene.setSpecificCameraInstructions("Follow the framing shown in the example");
        scene.setAudioNotes("Clear speech, match the tone of the scene");

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
    
    /**
     * Converts a video URL to GCS URI format for Video Intelligence API
     */
    private String toGcsUri(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) return videoUrl;
        if (videoUrl.startsWith("gs://")) return videoUrl;
        
        String httpsPrefix = "https://storage.googleapis.com/";
        if (videoUrl.startsWith(httpsPrefix)) {
            // https://storage.googleapis.com/<bucket>/<object>
            return "gs://" + videoUrl.substring(httpsPrefix.length());
        }
        
        // If caller passed just an object path, attach default bucket
        if (!videoUrl.contains("://")) {
            return "gs://" + bucketName + "/" + videoUrl.replaceFirst("^/", "");
        }
        
        // Fallback: return as-is (the API may reject non-GCS URIs)
        return videoUrl;
    }
}
// Change Log: Removed block grid services, simplified to use AI object detection only