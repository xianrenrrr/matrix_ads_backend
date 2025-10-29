package com.example.demo.ai.services;

// TODO: Replace FFmpeg scene detection with Azure Video Indexer or Alibaba Cloud Video AI
// See docs/AI_SYSTEM_ARCHITECTURE.md for migration plan
import java.net.URL;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.*;
import com.example.demo.ai.label.ObjectLabelService;
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
import java.util.Map;

@Service
public class TemplateAIServiceImpl implements TemplateAIService {
    private static final Logger log = LoggerFactory.getLogger(TemplateAIServiceImpl.class);

    @Autowired(required = false)
    private AlibabaVideoShotDetectionService shotDetectionService;
    
    @Autowired
    private SegmentationService segmentationService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    @Autowired
    private KeyframeExtractionService keyframeExtractionService;
    
    // VideoSummaryService removed - not used
    // VideoMetadataService removed - not used
    
    @Autowired
    private UnifiedSceneAnalysisService unifiedSceneAnalysisService;
    
    @Autowired(required = false)
    private com.example.demo.ai.subtitle.ASRSubtitleExtractor asrSubtitleExtractor;
    
    @Autowired
    private com.example.demo.service.AlibabaOssStorageService alibabaOssStorageService;
    
    @Value("${ai.template.useObjectOverlay:true}")
    private boolean useObjectOverlay;
    


    /**
     * USED BY: AI Template Creation
     * 
     * Overload 1/4: Simplest entry point with default Chinese language
     * Delegates to overload 4 with: language="zh-CN", userDescription=null, sceneThresholdOverride=null
     */
    @Override
    public ManualTemplate generateTemplate(Video video) {
        ManualTemplate template = generateTemplate(video, "zh-CN"); // Chinese-first approach
        template.setLocaleUsed("zh-CN");
        return template;
    }
    
    /**
     * USED BY: AI Template Creation
     * 
     * Overload 2/2: Main implementation with all parameters
     * 
     * Flow:
     * 1. Detect scenes from video using scene detection service (FFmpeg or Azure/Alibaba)
     * 2. For each detected scene:
     *    - Extract keyframe
     *    - Detect objects with YOLO (via UnifiedSceneAnalysisService)
     *    - Label objects with Qwen VL
     * 3. Generate template metadata and per-scene guidance with Qwen reasoning model
     * 4. Return complete template with AI-analyzed scenes
     * 
     * @param video Video object to analyze
     * @param language Language for AI analysis (e.g., "zh-CN", "en")
     * @param userDescription Optional user description to guide AI metadata generation
     * @param sceneThresholdOverride Optional scene detection sensitivity override (0.0-1.0)
     * @return ManualTemplate with AI-detected and analyzed scenes
     */
    @Override
    public ManualTemplate generateTemplate(Video video, String language, String userDescription) {
        log.info("Starting AI template generation for video ID: {} in language: {} with user description: {}", 
                 video.getId(), language, userDescription != null ? "provided" : "none");
        if (userDescription != null && !userDescription.trim().isEmpty()) {
            log.info("User description content: {}", userDescription);
        }
        log.info("Using Alibaba Cloud AI for automatic scene detection");

        try {
            // Step 1: Detect scenes using Alibaba Cloud Video Shot Detection
            log.info("Step 1: Detecting scenes with Alibaba Cloud...");
            
            String videoUrl = video.getUrl();
            
            // Check if shot detection service is available (requires SDK)
            if (shotDetectionService == null) {
                log.warn("Shot detection service not available (SDK not configured), returning fallback template");
                return createFallbackTemplate(video, language);
            }
            
            List<SceneSegment> sceneSegments = shotDetectionService.detectScenes(videoUrl);
            
            if (sceneSegments.isEmpty()) {
                log.info("No scenes detected, creating fallback template");
                return createFallbackTemplate(video, language);
            }

            // Step 2: Extract speech transcript using ASR
            log.info("Step 2: Extracting speech transcript using ASR");
            List<com.example.demo.ai.subtitle.SubtitleSegment> allAsrSegments = new ArrayList<>();
            if (asrSubtitleExtractor != null) {
                try {
                    // Generate signed URL with 1 hour expiration for ASR processing
                    String signedUrl = alibabaOssStorageService.generateSignedUrl(videoUrl, 60, java.util.concurrent.TimeUnit.MINUTES);
                    log.info("Generated signed URL for ASR (expires in 1 hour)");
                    
                    allAsrSegments = asrSubtitleExtractor.extract(signedUrl, language);
                    log.info("ASR extracted {} transcript segments", allAsrSegments.size());
                } catch (Exception e) {
                    log.warn("ASR extraction failed, continuing without transcript: {}", e.getMessage());
                }
            } else {
                log.warn("ASR service not available, scenes will not have script lines");
            }

            // Step 3: Create basic scene objects first
            log.info("Step 3: Creating {} scene objects", sceneSegments.size());
            List<Scene> scenes = new ArrayList<>();
            for (int i = 0; i < sceneSegments.size(); i++) {
                SceneSegment segment = sceneSegments.get(i);
                Scene scene = new Scene();
                scene.setSceneNumber(i + 1);
                scene.setStartTimeMs(segment.getStartTimeMs());
                scene.setEndTimeMs(segment.getEndTimeMs());
                scene.setSceneDurationInSeconds((segment.getEndTimeMs() - segment.getStartTimeMs()) / 1000);
                scene.setSceneSource("ai");
                scenes.add(scene);
            }
            
            // Step 4: Clean scriptLines and set to scenes
            log.info("Step 4: Cleaning scriptLines with {} ASR segments", allAsrSegments.size());
            if (!allAsrSegments.isEmpty()) {
                try {
                    // Build scene data for scriptLine cleaning
                    List<Map<String, Object>> sceneDataForCleaning = new ArrayList<>();
                    for (Scene scene : scenes) {
                        Map<String, Object> sceneMap = new HashMap<>();
                        sceneMap.put("sceneNumber", scene.getSceneNumber());
                        sceneMap.put("startMs", scene.getStartTimeMs());
                        sceneMap.put("endMs", scene.getEndTimeMs());
                        // Add video description for context
                        if (userDescription != null && !userDescription.isEmpty()) {
                            sceneMap.put("videoDescription", userDescription);
                        }
                        sceneDataForCleaning.add(sceneMap);
                    }
                    
                    // Convert ASR to Maps
                    List<Map<String, Object>> asrMaps = new ArrayList<>();
                    for (com.example.demo.ai.subtitle.SubtitleSegment seg : allAsrSegments) {
                        Map<String, Object> asrObj = new HashMap<>();
                        asrObj.put("startMs", seg.getStartTimeMs());
                        asrObj.put("endMs", seg.getEndTimeMs());
                        asrObj.put("text", seg.getText());
                        asrMaps.add(asrObj);
                    }
                    
                    // Call cleanScriptLines
                    Map<String, Object> scriptResult = objectLabelService.cleanScriptLines(asrMaps, sceneDataForCleaning);
                    if (scriptResult != null && scriptResult.containsKey("scriptLines")) {
                        @SuppressWarnings("unchecked")
                        List<String> lines = (List<String>) scriptResult.get("scriptLines");
                        
                        // Set scriptLines to scenes immediately
                        for (int i = 0; i < Math.min(scenes.size(), lines.size()); i++) {
                            scenes.get(i).setScriptLine(lines.get(i));
                            log.info("✅ Scene {} scriptLine: \"{}\"", i + 1, lines.get(i));
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ ScriptLine cleaning failed: {}", e.getMessage(), e);
                }
            }

            // Step 5: Process each scene with VL analysis (scriptLine already set)
            log.info("Step 5: Processing {} scenes with VL analysis", scenes.size());
            List<String> allSceneLabels = new ArrayList<>();

            for (int i = 0; i < sceneSegments.size(); i++) {
                SceneSegment segment = sceneSegments.get(i);
                Scene scene = scenes.get(i);
                log.info("Processing scene {}/{} with language: {}", i + 1, sceneSegments.size(), language);
                
                // Process scene WITH scriptLine context (already set)
                processSceneVL(scene, segment, video.getUrl(), language);
                
                // ScriptLine already set in Step 4
                
                // Collect labels for summary (no more block descriptions)
                if (segment.getLabels() != null) {
                    allSceneLabels.addAll(segment.getLabels());
                }
            }

            // Step 6: Create the template
            log.info("Step 6: Creating final template...");
            ManualTemplate template = new ManualTemplate();
            template.setVideoId(video.getId());
            template.setUserId(video.getUserId());
            String today = java.time.LocalDate.now().toString();
            template.setTemplateTitle((video.getTitle() != null && !video.getTitle().isBlank())
                ? (video.getTitle() + " - AI 模版 " + today)
                : ("AI 模版 " + today));
            template.setScenes(scenes);
            template.setTotalVideoLength(calculateTotalDuration(sceneSegments));
            
            // Step 7: AI-driven template metadata & per-scene guidance
            log.info("=== AI TEMPLATE GUIDANCE GENERATION START ===");
            log.info("About to call generateAIMetadata with {} scenes", scenes.size());
            try {
                generateAIMetadata(template, video, scenes, allSceneLabels, language, userDescription);
                log.info("=== AI TEMPLATE GUIDANCE GENERATION COMPLETE ===");
            } catch (Exception e) {
                log.error("=== AI TEMPLATE GUIDANCE GENERATION FAILED ===", e);
            }
            log.info("AI template generation completed for video ID: {} with {} scenes", 
                             video.getId(), scenes.size());
            return template;

        } catch (Exception e) {
            log.error("Error in AI template generation for video ID {}: {}", video.getId(), e.getMessage(), e);
            return createFallbackTemplate(video, language);
        }
    }

    /**
     * USED BY: AI Template Creation
     * 
     * Process a single detected scene segment with AI analysis
     * 
     * Flow:
     * 1. Create base scene with timing info from SceneSegment
     * 2. Call UnifiedSceneAnalysisService to analyze the scene:
     *    - Extract keyframe from video segment
     *    - Detect objects with YOLO
     *    - Label objects with Qwen VL (returns Chinese labels + scene analysis)
     * 3. Apply analysis results to scene (overlays, labels, VL data)
     * 5. Return fully analyzed scene
     * 
     * @param segment Scene timing info from scene detection
     * @param sceneNumber Scene number in template
     * @param videoUrl Original video URL
     * @param language Language for AI analysis
     * @return Scene with AI analysis (overlays, labels, metadata)
     */
    /**
     * Process scene with VL analysis (scene object already created with scriptLine)
     */
    private void processSceneVL(Scene scene, SceneSegment segment, String videoUrl, String language) {
        try {
            // Use unified scene analysis service (WITH scriptLine context)
            String scriptLine = scene.getScriptLine();
            log.info("Analyzing scene {} with scriptLine context: {}", scene.getSceneNumber(), 
                scriptLine != null && !scriptLine.isEmpty() ? "\"" + scriptLine + "\"" : "none");
            
            SceneAnalysisResult analysisResult = unifiedSceneAnalysisService.analyzeScene(
                videoUrl,
                null,  // auto-detect objects with YOLO
                language,
                segment.getStartTime(),
                segment.getEndTime(),
                scriptLine  // Pass scriptLine as context for VL
            );
            
            // Apply analysis results to scene
            analysisResult.applyToScene(scene);
            
        } catch (Exception e) {
            log.error("Scene analysis failed for scene {}: {} - {}", scene.getSceneNumber(), e.getClass().getSimpleName(), e.getMessage());
            // Continue with basic scene without overlays
        }
        
        // No grid fallback - if YOLO fails, continue without overlays
        // VL analysis is still valuable without object regions
        if (scene.getOverlayType() == null) {
            log.warn("Scene {} has no object overlays (YOLO detection may have failed or no objects detected)", scene.getSceneNumber());
        }
    }

    /**
     * USED BY: AI Template Creation
     * 
     * Generates template-level metadata and per-scene guidance using Qwen reasoning model
     * 
     * NOTE: Manual Template Creation uses similar logic in ContentManager.generateManualTemplateMetadata()
     * which directly calls objectLabelService.generateTemplateGuidance() without going through this service.
     * 
     * Flow:
     * 1. Build payload with template info + scene data (keyframes, detected objects, VL analysis)
     * 2. Add ALL ASR segments to payload for AI to clean and assign to scenes
     * 3. Call Qwen reasoning model via objectLabelService.generateTemplateGuidance()
     * 4. Apply returned metadata to template:
     *    - Template level: videoPurpose, tone, lightingRequirements, backgroundMusic
     *    - Scene level: presenceOfPerson, movementInstructions, backgroundInstructions, scriptLine (cleaned)
     * 5. Derive device orientation and video format from first scene's keyframe dimensions
     * 
     * @param template Template to populate with metadata
     * @param video Original video object
     * @param scenes List of analyzed scenes
     * @param sceneLabels Collected scene labels (legacy, may be unused)
     * @param language Language for AI generation
     * @param userDescription Optional user description to guide AI
     * @param allAsrSegments All ASR transcript segments for the entire video (to be cleaned by AI)
     */
    private void generateAIMetadata(ManualTemplate template, Video video, List<Scene> scenes, 
                                   List<String> sceneLabels, String language, String userDescription) {
        log.info("[METADATA] generateAIMetadata called with {} scenes, language={}, userDescription={}", 
            scenes.size(), language, userDescription != null ? "provided" : "null");
        try {
            // Build compact payload for one-shot Chinese guidance
            Map<String, Object> payload = new java.util.HashMap<>();
            Map<String, Object> tpl = new java.util.HashMap<>();
            tpl.put("videoTitle", video.getTitle());
            tpl.put("language", language);
            tpl.put("totalDurationSeconds", template.getTotalVideoLength());
            tpl.put("videoFormat", template.getVideoFormat());
            if (userDescription != null && !userDescription.isBlank()) {
                tpl.put("userDescription", userDescription);
            }
            payload.put("template", tpl);

            java.util.List<Map<String, Object>> sceneArr = new java.util.ArrayList<>();
            for (Scene s : scenes) {
                Map<String, Object> so = new java.util.HashMap<>();
                so.put("sceneNumber", s.getSceneNumber());
                so.put("durationSeconds", s.getSceneDurationInSeconds());
                so.put("startMs", s.getStartTimeMs());
                so.put("endMs", s.getEndTimeMs());
                so.put("keyframeUrl", s.getKeyframeUrl());
                
                // Collect top-K detected object labels (Chinese) for context
                java.util.List<String> labels = new java.util.ArrayList<>();
                if (s.getOverlayPolygons() != null) {
                    for (var p : s.getOverlayPolygons()) {
                        if (p.getLabelLocalized() != null && !p.getLabelLocalized().isEmpty()) labels.add(p.getLabelLocalized());
                        else if (p.getLabelZh() != null && !p.getLabelZh().isEmpty()) labels.add(p.getLabelZh());
                    }
                }
                if (s.getOverlayObjects() != null) {
                    for (var o : s.getOverlayObjects()) {
                        if (o.getLabelLocalized() != null && !o.getLabelLocalized().isEmpty()) labels.add(o.getLabelLocalized());
                        else if (o.getLabelZh() != null && !o.getLabelZh().isEmpty()) labels.add(o.getLabelZh());
                    }
                }
                if (labels.size() > 5) labels = labels.subList(0, 5);
                so.put("detectedObjects", labels);
                
                // Add VL scene analysis for richer context
                if (s.getVlSceneAnalysis() != null && !s.getVlSceneAnalysis().isEmpty()) {
                    so.put("sceneAnalysis", s.getVlSceneAnalysis());
                }
                
                // Add scene description if available (for manual templates)
                if (s.getSceneDescription() != null && !s.getSceneDescription().isEmpty()) {
                    so.put("sceneDescription", s.getSceneDescription());
                }
                
                log.info("=== SCENE {} DATA FOR AI ===", s.getSceneNumber());
                log.info("Duration: {} seconds", s.getSceneDurationInSeconds());
                log.info("Time range: {}-{}ms", s.getStartTimeMs(), s.getEndTimeMs());
                log.info("Detected Objects: {}", labels);
                log.info("VL Scene Analysis: {}", s.getVlSceneAnalysis() != null ? s.getVlSceneAnalysis() : "null");
                log.info("Scene Description: {}", s.getSceneDescription() != null ? s.getSceneDescription() : "null");
                log.info("===========================");
                
                sceneArr.add(so);
            }
            payload.put("scenes", sceneArr);
            
            log.info("=== FULL PAYLOAD TO AI ===");
            log.info("Template info: videoTitle={}, language={}, totalDuration={}, userDescription={}", 
                video.getTitle(), language, template.getTotalVideoLength(), userDescription);
            log.info("Total scenes: {}", sceneArr.size());
            log.info("==========================");

            // Combine all scriptLines for AI context
            String combinedScriptLines = scenes.stream()
                .map(Scene::getScriptLine)
                .filter(sl -> sl != null && !sl.isEmpty())
                .collect(java.util.stream.Collectors.joining(" | "));
            
            if (!combinedScriptLines.isEmpty()) {
                tpl.put("combinedScriptLines", combinedScriptLines);
                log.info("[METADATA] Combined scriptLines for AI: \"{}\"", combinedScriptLines);
            }
            
            // BACKUP: Save VL data before guidance generation (in case it gets lost)
            Map<Integer, String> vlRawBackup = new java.util.HashMap<>();
            Map<Integer, String> vlAnalysisBackup = new java.util.HashMap<>();
            for (Scene s : scenes) {
                if (s.getVlRawResponse() != null) {
                    vlRawBackup.put(s.getSceneNumber(), s.getVlRawResponse());
                }
                if (s.getVlSceneAnalysis() != null) {
                    vlAnalysisBackup.put(s.getSceneNumber(), s.getVlSceneAnalysis());
                }
            }
            
            // Generate template metadata
            log.info("[METADATA] Calling objectLabelService.generateTemplateGuidance");
            Map<String, Object> result = objectLabelService.generateTemplateGuidance(payload);
            log.info("[METADATA] generateTemplateGuidance returned: {}", result != null ? "result with " + result.size() + " keys" : "null");
            if (result == null || result.isEmpty()) {
                log.warn("[METADATA] AI guidance unavailable; leaving metadata/guidance empty (no presets)");
                return;
            }
            
            // RESTORE: Ensure VL data is preserved after guidance generation
            for (Scene s : scenes) {
                int sceneNum = s.getSceneNumber();
                
                // Restore if lost
                if (s.getVlRawResponse() == null && vlRawBackup.containsKey(sceneNum)) {
                    s.setVlRawResponse(vlRawBackup.get(sceneNum));
                    log.warn("[VL] Restored vlRawResponse for scene {} ({} chars)", sceneNum, vlRawBackup.get(sceneNum).length());
                }
                if (s.getVlSceneAnalysis() == null && vlAnalysisBackup.containsKey(sceneNum)) {
                    s.setVlSceneAnalysis(vlAnalysisBackup.get(sceneNum));
                    log.warn("[VL] Restored vlSceneAnalysis for scene {} ({} chars)", sceneNum, vlAnalysisBackup.get(sceneNum).length());
                }
                
                log.info("[DEBUG] Scene {} AFTER guidance - vlRawResponse: {}, vlSceneAnalysis: {}", 
                    sceneNum,
                    s.getVlRawResponse() != null ? s.getVlRawResponse().length() + " chars" : "null",
                    s.getVlSceneAnalysis() != null ? s.getVlSceneAnalysis().length() + " chars" : "null");
            }

            // Apply template metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> t = (Map<String, Object>) result.get("template");
            log.info("[METADATA] Template metadata from AI: {}", t != null ? t.keySet() : "null");
            if (t != null) {
                Object vp = t.get("videoPurpose"); if (vp instanceof String s) { template.setVideoPurpose(trim40(s)); log.info("[METADATA] Set videoPurpose: {}", s); }
                Object tone = t.get("tone"); if (tone instanceof String s) { template.setTone(trim40(s)); log.info("[METADATA] Set tone: {}", s); }
                Object light = t.get("lightingRequirements"); if (light instanceof String s) { template.setLightingRequirements(trim60(s)); log.info("[METADATA] Set lightingRequirements: {}", s); }
                Object bgm = t.get("backgroundMusic"); if (bgm instanceof String s) { template.setBackgroundMusic(trim40(s)); log.info("[METADATA] Set backgroundMusic: {}", s); }
            }

            // Apply per-scene guidance
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> rs = (java.util.List<Map<String, Object>>) result.get("scenes");
            log.info("[METADATA] Scene guidance from AI: {} scenes", rs != null ? rs.size() : "null");
            if (rs != null) {
                java.util.Map<Integer, Map<String, Object>> byNum = new java.util.HashMap<>();
                for (Map<String, Object> one : rs) {
                    Object n = one.get("sceneNumber");
                    if (n instanceof Number num) byNum.put(num.intValue(), one);
                }
                for (Scene s : scenes) {
                    Map<String, Object> one = byNum.get(s.getSceneNumber());
                    if (one == null) continue;
                    
                    Object person = one.get("presenceOfPerson"); if (person instanceof Boolean b) s.setPresenceOfPerson(b);
                    Object move = one.get("movementInstructions"); if (move instanceof String v) s.setMovementInstructions(trim60(v));
                    Object bg = one.get("backgroundInstructions"); if (bg instanceof String v) s.setBackgroundInstructions(trim60(v));
                    Object cam = one.get("specificCameraInstructions"); if (cam instanceof String v) s.setSpecificCameraInstructions(trim60(v));
                    Object audio = one.get("audioNotes"); if (audio instanceof String v) s.setAudioNotes(trim60(v));
                }
            }
            

            // Device orientation: derive from keyframe and apply to all scenes
            String aspectRatio = deriveDeviceOrientationFromFirstScene(scenes, language);
            if (aspectRatio != null) {
                for (Scene s : scenes) {
                    s.setDeviceOrientation(aspectRatio);  // e.g., "9:16" or "16:9"
                }
                
                // Derive video format from aspect ratio
                String videoFormat = "1080p " + aspectRatio;
                template.setVideoFormat(videoFormat);
                log.info("Set video format from aspect ratio: {}", videoFormat);
            } else {
                template.setVideoFormat("1080p 16:9");  // Fallback
                log.warn("Could not derive aspect ratio, using fallback format");
            }
        } catch (Exception e) {
            log.info("AI guidance generation failed: {}. Leaving fields empty.", e.getMessage());
            // Still derive and set device orientation even if AI guidance failed
            String aspectRatio = deriveDeviceOrientationFromFirstScene(scenes, language);
            if (aspectRatio != null) {
                for (Scene s : scenes) {
                    s.setDeviceOrientation(aspectRatio);  // e.g., "9:16" or "16:9"
                }
                
                // Derive video format from aspect ratio
                String videoFormat = "1080p " + aspectRatio;
                template.setVideoFormat(videoFormat);
            }
        }
    }

    /**
     * USED BY: Both AI Template Creation AND Manual Template Creation
     * Utility methods for trimming AI-generated text to field length limits
     */
    private String trim40(String s) { return s == null ? null : (s.length() > 40 ? s.substring(0,40) : s); }
    private String trim60(String s) { return s == null ? null : (s.length() > 60 ? s.substring(0,60) : s); }
    
    /**
     * Convert ASR segments to Map format for AI processing
     */
    private List<Map<String, Object>> convertAsrToMaps(List<com.example.demo.ai.subtitle.SubtitleSegment> asrSegments) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.example.demo.ai.subtitle.SubtitleSegment seg : asrSegments) {
            Map<String, Object> asrObj = new HashMap<>();
            asrObj.put("startMs", seg.getStartTimeMs());
            asrObj.put("endMs", seg.getEndTimeMs());
            asrObj.put("text", seg.getText());
            result.add(asrObj);
        }
        return result;
    }

    /**
     * USED BY: Both AI Template Creation AND Manual Template Creation
     * Derives device orientation (aspect ratio) from first scene's keyframe dimensions
     * Returns "9:16" for portrait or "16:9" for landscape
     */
    private String deriveDeviceOrientationFromFirstScene(List<Scene> scenes, String language) {
        try {
            // Get aspect ratio from keyframe dimensions
            for (Scene s : scenes) {
                if (s.getKeyframeUrl() == null || s.getKeyframeUrl().isBlank()) continue;
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.net.URL(s.getKeyframeUrl()));
                if (img == null) continue;
                int w = img.getWidth(), h = img.getHeight();
                boolean portrait = h >= w;
                // Return aspect ratio: "9:16" or "16:9"
                return portrait ? "9:16" : "16:9";
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    /**
     * Calculate total duration from scene segments
     */
    private int calculateTotalDuration(List<SceneSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return 0;
        }
        
        // Find the maximum end time
        long maxEndMs = 0;
        for (SceneSegment segment : segments) {
            if (segment.getEndTimeMs() != null && segment.getEndTimeMs() > maxEndMs) {
                maxEndMs = segment.getEndTimeMs();
            }
        }
        
        // Convert to seconds and cast to int
        return (int) (maxEndMs / 1000);
    }
    
    /**
     * Extract script text for a specific scene based on timestamps
     * Matches transcript segments that overlap with the scene time range
     *
    /**
     * Create a fallback template with a single scene when shot detection fails
     */
    private ManualTemplate createFallbackTemplate(Video video, String language) {
        log.info("Creating fallback template for video: {}", video.getId());
        
        ManualTemplate template = new ManualTemplate();
        template.setVideoId(video.getId());
        template.setUserId(video.getUserId());
        
        String today = java.time.LocalDate.now().toString();
        template.setTemplateTitle((video.getTitle() != null && !video.getTitle().isBlank())
            ? (video.getTitle() + " - AI 模版 " + today)
            : ("AI 模版 " + today));
        
        // Create a single scene covering the whole video
        Scene scene = new Scene();
        scene.setSceneNumber(1);
        scene.setSceneTitle("zh-CN".equals(language) ? "完整视频" : "Full Video");
        scene.setSceneDurationInSeconds(30); // Default duration
        scene.setScriptLine("");
        scene.setPresenceOfPerson(false);
        scene.setDeviceOrientation("portrait");
        scene.setBackgroundInstructions("");
        scene.setSpecificCameraInstructions("");
        scene.setMovementInstructions("");
        scene.setAudioNotes("");
        scene.setSceneSource("ai");
        scene.setOverlayType("grid");
        
        template.setScenes(List.of(scene));
        template.setTotalVideoLength(30);
        
        return template;
    }



}
// Change Log: Removed block grid services, simplified to use AI object detection only
