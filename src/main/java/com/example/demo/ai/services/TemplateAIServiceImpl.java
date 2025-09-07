package com.example.demo.ai.services;

import com.example.demo.ai.providers.vision.FFmpegSceneDetectionService;
import com.example.demo.ai.providers.llm.VideoSummaryService;
import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.*;
import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.ai.util.ImageCropper;
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
    private SegmentationService segmentationService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
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
        ManualTemplate template = generateTemplate(video, "zh-CN"); // Chinese-first approach
        template.setLocaleUsed("zh-CN");
        return template;
    }
    
    @Override
    public ManualTemplate generateTemplate(Video video, String language) {
        return generateTemplate(video, language, null);
    }
    
    @Override
    public ManualTemplate generateTemplate(Video video, String language, String userDescription) {
        log.info("Starting AI template generation for video ID: {} in language: {} with user description: {}", 
                 video.getId(), language, userDescription != null ? "provided" : "none");
        if (userDescription != null && !userDescription.trim().isEmpty()) {
            log.info("User description content: {}", userDescription);
        }

        try {
            // Step 1: Detect scenes using FFmpeg (Chinese-first workflow)
            log.info("Step 1: Detecting scenes with FFmpeg...");
            
            String videoUrl = video.getUrl();
            
            // Use FFmpeg for scene detection instead of Google Video Intelligence
            List<SceneSegment> sceneSegments = sceneDetectionService.detectScenes(videoUrl);
            
            if (sceneSegments.isEmpty()) {
                log.info("No scenes detected, creating fallback template");
                return createFallbackTemplate(video, language);
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
            
            // Generate AI-powered template metadata in the target language
            log.info("=== AI TEMPLATE METADATA GENERATION ===");
            log.info("Generating template metadata using AI in language: {} with user description: {}", 
                     language, userDescription != null ? "provided" : "none");
            
            // Use AI to generate metadata based on video analysis and user description
            generateAIMetadata(template, video, scenes, allSceneLabels, language, userDescription);

            // Step 4: Generate summary (optional) - simplified without block descriptions
            log.info("Step 4: Generating video summary with user description...");
            String summary = videoSummaryService.generateSummary(video, allSceneLabels, new HashMap<>(), language, userDescription);
            log.info("Generated summary: {}", summary);
            
            log.info("AI template generation completed for video ID: {} with {} scenes", 
                             video.getId(), scenes.size());
            return template;

        } catch (Exception e) {
            log.error("Error in AI template generation for video ID {}: {}", video.getId(), e.getMessage(), e);
            return createFallbackTemplate(video, language);
        }
    }

    private Scene processScene(SceneSegment segment, int sceneNumber, String videoUrl, String language) {
        // Create base scene with clean data
        Scene scene = SceneProcessor.createFromSegment(segment, sceneNumber, language);
        
        // Extract keyframe if possible
        String keyframeUrl = extractKeyframe(scene, segment, videoUrl);
        if (keyframeUrl != null) {
            scene.setKeyframeUrl(keyframeUrl);
            scene.setExampleFrame(keyframeUrl);
            
            // NEW: Use segmentation service for shape detection
            processSceneWithShapes(scene, keyframeUrl, language);
        }
        
        // Process overlays in a clean, separated way using AI orchestrator (fallback)
        if (scene.getOverlayType() == null) {
            OverlayProcessor overlayProcessor = new OverlayProcessor(
                aiOrchestrator, overlayLegendService);
            overlayProcessor.processOverlays(scene, segment, keyframeUrl);
        }
        
        return scene;
    }
    
    private void processSceneWithShapes(Scene scene, String keyframeUrl, String language) {
        try {
            // Detect shapes using segmentation service
            List<OverlayShape> shapes = segmentationService.detect(keyframeUrl);
            
            if (!shapes.isEmpty()) {
                // Process shapes and add Chinese labels
                boolean hasPolygons = false;
                boolean hasBoxes = false;
                
                for (OverlayShape shape : shapes) {
                    // Crop and get Chinese label
                    byte[] cropBytes = ImageCropper.crop(keyframeUrl, shape);
                    String labelZh = objectLabelService.labelZh(cropBytes);
                    
                    // Create new shape with Chinese label
                    if (shape instanceof OverlayPolygon polygon) {
                        hasPolygons = true;
                        OverlayPolygon newPolygon = new OverlayPolygon(
                            polygon.label(),
                            labelZh,
                            polygon.confidence(),
                            polygon.points()
                        );
                        if (scene.getOverlayPolygons() == null) {
                            scene.setOverlayPolygons(new ArrayList<>());
                        }
                        // Convert our DTO polygon to GoogleVisionProvider.OverlayPolygon
                        com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon scenePolygon = new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon();
                        scenePolygon.setLabel(newPolygon.label());
                        scenePolygon.setLabelZh(newPolygon.labelZh());
                        // Ensure localized label is populated for frontend legend/UI
                        scenePolygon.setLabelLocalized(newPolygon.labelZh());
                        scenePolygon.setConfidence((float) newPolygon.confidence());
                        
                        // Convert points using the nested Point class
                        List<com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point> scenePoints = new ArrayList<>();
                        for (com.example.demo.ai.seg.dto.Point p : newPolygon.points()) {
                            com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point scenePoint = new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point((float) p.x(), (float) p.y());
                            scenePoints.add(scenePoint);
                        }
                        scenePolygon.setPoints(scenePoints);
                        
                        scene.getOverlayPolygons().add(scenePolygon);
                    } else if (shape instanceof OverlayBox box) {
                        hasBoxes = true;
                        OverlayBox newBox = new OverlayBox(
                            box.label(),
                            labelZh,
                            box.confidence(),
                            box.x(), box.y(), box.w(), box.h()
                        );
                        if (scene.getOverlayObjects() == null) {
                            scene.setOverlayObjects(new ArrayList<>());
                        }
                        scene.getOverlayObjects().add(convertToSceneBox(newBox));
                    }
                }
                
                // Set overlay type priority: polygons > objects > grid
                if (hasPolygons) {
                    scene.setOverlayType("polygons");
                } else if (hasBoxes) {
                    scene.setOverlayType("objects");
                } else {
                    scene.setOverlayType("grid");
                }
                
                // Build legend to drive mini‑app overlay UI if not grid
                if (!"grid".equals(scene.getOverlayType()) && overlayLegendService != null) {
                    var legend = overlayLegendService.buildLegend(scene, language != null ? language : "zh-CN");
                    scene.setLegend(legend);
                }

                // Set dominant object's Chinese label as scene's short label
                if (!shapes.isEmpty()) {
                    OverlayShape dominant = shapes.get(0); // Already sorted by conf×area
                    scene.setShortLabelZh(dominant.labelZh());
                    
                    // Generate scene description in Chinese
                    String fullFrameLabel = objectLabelService.labelZh(ImageCropper.crop(keyframeUrl, 
                        new OverlayBox("", "", 1.0, 0, 0, 1, 1)));
                    scene.setSceneDescriptionZh("场景包含" + fullFrameLabel);
                }
            } else {
                // No shapes detected here; leave overlayType unset so downstream
                // OverlayProcessor can attempt orchestrator-based polygon detection
                // or apply grid as a final fallback.
            }
        } catch (Exception e) {
            log.error("Failed to process scene with shapes: {}", e.getMessage());
            // Leave overlayType unset to allow downstream fallback processing
        }
    }
    
    // Removed convertToScenePolygon method - conversion now done inline
    
    private com.example.demo.model.Scene.ObjectOverlay convertToSceneBox(OverlayBox box) {
        com.example.demo.model.Scene.ObjectOverlay model = new com.example.demo.model.Scene.ObjectOverlay();
        model.setLabel(box.label());
        model.setLabelZh(box.labelZh());
        // Populate labelLocalized for UI that prefers localized labels
        model.setLabelLocalized(box.labelZh());
        model.setConfidence((float) box.confidence());
        model.setX((float) box.x());
        model.setY((float) box.y());
        model.setWidth((float) box.w());
        model.setHeight((float) box.h());
        return model;
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

    
    private ManualTemplate createFallbackTemplate(Video video, String language) {
        log.info("Creating fallback template due to processing failure in language: {}", language);
        
        ManualTemplate template = new ManualTemplate();
        template.setVideoId(video.getId());
        template.setUserId(video.getUserId());
        
        if ("zh".equals(language) || "zh-CN".equals(language)) {
            // Chinese fallback template
            template.setTemplateTitle(video.getTitle() + " - 基础模板");
            template.setVideoPurpose("基础视频内容展示");
            template.setTone("专业");
            template.setLightingRequirements("需要良好的照明");
            template.setBackgroundMusic("建议使用轻柔的背景音乐");
        } else {
            // English fallback template
            template.setTemplateTitle(video.getTitle() + " - Basic Template");
            template.setVideoPurpose("Basic video content showcase");
            template.setTone("Professional");
            template.setLightingRequirements("Good lighting required");
            template.setBackgroundMusic("Light background music recommended");
        }
        
        template.setVideoFormat("1080p 16:9");
        template.setTotalVideoLength(30); // Default 30 seconds

        // Create a single default scene with language support
        Scene defaultScene = new Scene();
        defaultScene.setSceneNumber(1);
        defaultScene.setSceneDurationInSeconds(30);
        defaultScene.setPresenceOfPerson(true);
        
        if ("zh".equals(language) || "zh-CN".equals(language)) {
            // Chinese scene metadata
            defaultScene.setSceneTitle("主场景");
            defaultScene.setScriptLine("请按照模板指南录制您的内容");
            defaultScene.setPreferredGender("无偏好");
            defaultScene.setPersonPosition("居中");
            defaultScene.setDeviceOrientation("手机（竖屏 9:16）");
            defaultScene.setMovementInstructions("静止");
            defaultScene.setBackgroundInstructions("使用干净、专业的背景");
            defaultScene.setSpecificCameraInstructions("从胸部以上拍摄，直视摄像头");
            defaultScene.setAudioNotes("说话清楚，语速适中");
        } else {
            // English scene metadata
            defaultScene.setSceneTitle("Main Scene");
            defaultScene.setScriptLine("Please record your content following the template guidelines");
            defaultScene.setPreferredGender("No Preference");
            defaultScene.setPersonPosition("Center");
            defaultScene.setDeviceOrientation("Phone (Portrait 9:16)");
            defaultScene.setMovementInstructions("Static");
            defaultScene.setBackgroundInstructions("Use a clean, professional background");
            defaultScene.setSpecificCameraInstructions("Frame yourself from chest up, looking directly at camera");
            defaultScene.setAudioNotes("Speak clearly and at moderate pace");
        }

        template.setScenes(List.of(defaultScene));
        return template;
    }
    
    private void generateAIMetadata(ManualTemplate template, Video video, List<Scene> scenes, 
                                   List<String> sceneLabels, String language, String userDescription) {
        try {
            // Try to use AI for metadata generation
            if (aiOrchestrator != null) {
                var response = aiOrchestrator.<com.example.demo.ai.providers.llm.LLMProvider.TemplateMetadata>executeWithFallback(
                    com.example.demo.ai.core.AIModelType.LLM,
                    "generateTemplateMetadata",
                    provider -> {
                        var llmProvider = (com.example.demo.ai.providers.llm.LLMProvider) provider;
                        
                        // Create a template metadata request with user description
                        var request = new com.example.demo.ai.providers.llm.LLMProvider.TemplateMetadataRequest();
                        request.setVideoTitle(video.getTitle());
                        request.setUserDescription(userDescription); // Include user's custom description
                        request.setSceneCount(scenes.size());
                        request.setTotalDuration(template.getTotalVideoLength());
                        request.setSceneLabels(sceneLabels);
                        request.setLanguage(language);
                        
                        // NEW: Add individual scene timing information
                        List<com.example.demo.ai.providers.llm.LLMProvider.SceneTimingInfo> sceneTimings = new ArrayList<>();
                        for (Scene scene : scenes) {
                            var timingInfo = new com.example.demo.ai.providers.llm.LLMProvider.SceneTimingInfo();
                            timingInfo.setSceneNumber(scene.getSceneNumber());
                            timingInfo.setStartSeconds(scene.getStartTime() != null ? (int) scene.getStartTime().getSeconds() : 0);
                            timingInfo.setEndSeconds(scene.getEndTime() != null ? (int) scene.getEndTime().getSeconds() : (int) scene.getSceneDurationInSeconds());
                            timingInfo.setDurationSeconds((int) scene.getSceneDurationInSeconds());
                            
                            // Add detected objects for this specific scene
                            List<String> sceneObjects = new ArrayList<>();
                            if (scene.getOverlayObjects() != null) {
                                scene.getOverlayObjects().forEach(obj -> {
                                    if (obj.getLabel() != null) sceneObjects.add(obj.getLabel());
                                });
                            }
                            if (scene.getOverlayPolygons() != null) {
                                scene.getOverlayPolygons().forEach(poly -> {
                                    if (poly.getLabel() != null) sceneObjects.add(poly.getLabel());
                                });
                            }
                            timingInfo.setDetectedObjects(sceneObjects);
                            
                            sceneTimings.add(timingInfo);
                        }
                        request.setSceneTimings(sceneTimings);
                        
                        return llmProvider.generateTemplateMetadata(request);
                    }
                );
                
                if (response.isSuccess() && response.getData() != null) {
                    var metadata = response.getData();
                    // Apply AI-generated metadata to template
                    applyAIGeneratedTemplateMetadata(template, metadata, scenes, language);
                    log.info("Applied AI-generated template metadata using {}", response.getModelUsed());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate AI metadata: {}", e.getMessage());
        }
        
        // Fallback to defaults
        applyDefaultMetadata(template, language);
    }
    
    private void applyAIGeneratedTemplateMetadata(ManualTemplate template, 
                                                 com.example.demo.ai.providers.llm.LLMProvider.TemplateMetadata metadata,
                                                 List<Scene> scenes, String language) {
        // Apply basic template metadata
        if (metadata.getVideoPurpose() != null) {
            template.setVideoPurpose(metadata.getVideoPurpose());
        }
        if (metadata.getTone() != null) {
            template.setTone(metadata.getTone());
        }
        if (metadata.getVideoFormat() != null) {
            template.setVideoFormat(metadata.getVideoFormat());
        }
        if (metadata.getLightingRequirements() != null) {
            template.setLightingRequirements(metadata.getLightingRequirements());
        }
        if (metadata.getBackgroundMusic() != null) {
            template.setBackgroundMusic(metadata.getBackgroundMusic());
        }
        
        // Apply scene-specific metadata if available
        if (metadata.getSceneMetadataList() != null && !metadata.getSceneMetadataList().isEmpty()) {
            for (int i = 0; i < scenes.size() && i < metadata.getSceneMetadataList().size(); i++) {
                Scene scene = scenes.get(i);
                com.example.demo.ai.providers.llm.LLMProvider.SceneMetadata sceneMetadata = metadata.getSceneMetadataList().get(i);
                
                // Update scene with AI-generated metadata
                if (sceneMetadata.getSceneTitle() != null) {
                    scene.setSceneTitle(sceneMetadata.getSceneTitle());
                }
                if (sceneMetadata.getScriptLine() != null) {
                    scene.setScriptLine(sceneMetadata.getScriptLine());
                }
                scene.setPresenceOfPerson(sceneMetadata.isPresenceOfPerson());
                if (sceneMetadata.getDeviceOrientation() != null) {
                    scene.setDeviceOrientation(sceneMetadata.getDeviceOrientation());
                }
                if (sceneMetadata.getMovementInstructions() != null) {
                    scene.setMovementInstructions(sceneMetadata.getMovementInstructions());
                }
                if (sceneMetadata.getBackgroundInstructions() != null) {
                    scene.setBackgroundInstructions(sceneMetadata.getBackgroundInstructions());
                }
                if (sceneMetadata.getCameraInstructions() != null) {
                    scene.setSpecificCameraInstructions(sceneMetadata.getCameraInstructions());
                }
                if (sceneMetadata.getAudioNotes() != null) {
                    scene.setAudioNotes(sceneMetadata.getAudioNotes());
                }
                // Note: activeGridBlock might need mapping to grid overlay system
            }
        }
    }
    
    private void applyDefaultMetadata(ManualTemplate template, String language) {
        if ("zh".equals(language) || "zh-CN".equals(language)) {
            template.setVideoPurpose("产品展示与推广");
            template.setTone("专业");
            template.setLightingRequirements("良好的自然光或人工照明");
            template.setBackgroundMusic("轻柔的器乐或环境音乐");
        } else {
            template.setVideoPurpose("Product demonstration and promotion");
            template.setTone("Professional");
            template.setLightingRequirements("Good natural or artificial lighting");
            template.setBackgroundMusic("Soft instrumental or ambient music");
        }
    }
    
    
}
// Change Log: Removed block grid services, simplified to use AI object detection only
