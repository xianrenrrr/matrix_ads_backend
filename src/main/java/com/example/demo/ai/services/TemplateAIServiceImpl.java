package com.example.demo.ai.services;

import com.example.demo.ai.providers.vision.FFmpegSceneDetectionService;
import com.example.demo.ai.providers.llm.VideoSummaryService;
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
            log.info("Generating template metadata using AI in language: {}", language);
            
            // Use AI to generate metadata based on video analysis
            generateAIMetadata(template, video, scenes, allSceneLabels, language);

            // Step 4: Generate summary (optional) - simplified without block descriptions
            log.info("Step 4: Generating video summary...");
            String summary = videoSummaryService.generateSummary(video, allSceneLabels, new HashMap<>(), language);
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
                                   List<String> sceneLabels, String language) {
        try {
            // Try to use AI for metadata generation
            if (aiOrchestrator != null) {
                var response = aiOrchestrator.<com.example.demo.ai.providers.llm.LLMProvider.SceneSuggestions>executeWithFallback(
                    com.example.demo.ai.core.AIModelType.LLM,
                    "generateTemplateMetadata",
                    provider -> {
                        var llmProvider = (com.example.demo.ai.providers.llm.LLMProvider) provider;
                        
                        // Create a scene suggestions request for metadata
                        var request = new com.example.demo.ai.providers.llm.LLMProvider.SceneSuggestionsRequest();
                        request.setSceneTitle(video.getTitle());
                        
                        // Add scene context
                        java.util.Map<String, Object> analysisData = new java.util.HashMap<>();
                        analysisData.put("sceneCount", scenes.size());
                        analysisData.put("totalDuration", template.getTotalVideoLength());
                        analysisData.put("sceneLabels", sceneLabels);
                        analysisData.put("language", language);
                        request.setAnalysisData(analysisData);
                        
                        return llmProvider.generateSceneSuggestions(request);
                    }
                );
                
                if (response.isSuccess() && response.getData() != null) {
                    var suggestions = response.getData();
                    // Extract metadata from suggestions
                    applyAIGeneratedMetadata(template, suggestions, language);
                    log.info("Applied AI-generated metadata using {}", response.getModelUsed());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate AI metadata: {}", e.getMessage());
        }
        
        // Fallback to defaults
        applyDefaultMetadata(template, language);
    }
    
    private void applyAIGeneratedMetadata(ManualTemplate template, 
                                         com.example.demo.ai.providers.llm.LLMProvider.SceneSuggestions suggestions,
                                         String language) {
        // Use AI suggestions to set metadata
        if (suggestions.getSuggestionsZh() != null && !suggestions.getSuggestionsZh().isEmpty()) {
            // Parse first suggestion for video purpose
            String firstSuggestion = suggestions.getSuggestionsZh().get(0);
            if (firstSuggestion.length() <= 10) {
                template.setVideoPurpose(firstSuggestion);
            } else {
                template.setVideoPurpose(firstSuggestion.substring(0, 10));
            }
            
            // Use other suggestions for tone and requirements
            if (suggestions.getSuggestionsZh().size() > 1) {
                String tone = extractTone(suggestions.getSuggestionsZh().get(1));
                template.setTone(tone);
            }
        }
        
        // Set lighting and music based on analysis
        if ("zh".equals(language) || "zh-CN".equals(language)) {
            template.setLightingRequirements("智能分析建议的照明条件");
            template.setBackgroundMusic("AI推荐的背景音乐");
        } else {
            template.setLightingRequirements("AI-analyzed lighting conditions");
            template.setBackgroundMusic("AI-recommended background music");
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
    
    private String extractTone(String suggestion) {
        // Extract tone from suggestion text
        if (suggestion.contains("专业")) return "专业";
        if (suggestion.contains("轻松")) return "轻松";
        if (suggestion.contains("正式")) return "正式";
        if (suggestion.contains("casual")) return "Casual";
        if (suggestion.contains("formal")) return "Formal";
        return "专业"; // Default
    }
    
}
// Change Log: Removed block grid services, simplified to use AI object detection only