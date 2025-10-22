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

    // TODO: Replace with Azure Video Indexer or Alibaba Cloud Video AI scene detection
    // @Autowired
    // private SceneDetectionService sceneDetectionService;
    
    @Autowired
    private SegmentationService segmentationService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    
    @Autowired
    private OverlayLegendService overlayLegendService;
    
    @Autowired
    private KeyframeExtractionService keyframeExtractionService;
    
    // VideoSummaryService removed - not used
    // VideoMetadataService removed - not used
    
    @Autowired
    private UnifiedSceneAnalysisService unifiedSceneAnalysisService;
    
    @Value("${ai.template.useObjectOverlay:true}")
    private boolean useObjectOverlay;
    
    @Value("${firebase.storage.bucket}")
    private String bucketName;

    @Value("${AI_YOLO_ENABLED:${ai.overlay.yoloFallback.enabled:true}}")
    private boolean yoloFallbackEnabled;

    @Value("${AI_YOLO_ENDPOINT:}")
    private String hfYoloEndpoint;

    @Value("${AI_YOLO_API_KEY:}")
    private String hfYoloApiKey;

    @Value("${AI_YOLO_MIN_AREA:0.02}")
    private double hfMinArea;

    @Value("${AI_YOLO_MAX_OBJECTS:4}")
    private int hfMaxObjects;

    @Value("${guidance.scripts.enforceMaxLen:true}")
    private boolean enforceScriptMaxLen;

    @Value("${ai.overlay.includeLegend:false}")
    private boolean includeLegend;

    @Override
    public ManualTemplate generateTemplate(Video video) {
        ManualTemplate template = generateTemplate(video, "zh-CN"); // Chinese-first approach
        template.setLocaleUsed("zh-CN");
        return template;
    }
    
    @Override
    public ManualTemplate generateTemplate(Video video, String language) {
        return generateTemplate(video, language, null, null);
    }
    
    @Override
    public ManualTemplate generateTemplate(Video video, String language, String userDescription) {
        return generateTemplate(video, language, userDescription, null);
    }
    
    @Override
    public ManualTemplate generateTemplate(Video video, String language, String userDescription, Double sceneThresholdOverride) {
        log.info("Starting AI template generation for video ID: {} in language: {} with user description: {}", 
                 video.getId(), language, userDescription != null ? "provided" : "none");
        if (userDescription != null && !userDescription.trim().isEmpty()) {
            log.info("User description content: {}", userDescription);
        }
        if (sceneThresholdOverride != null) {
            log.info("Scene detection threshold override provided: {}", sceneThresholdOverride);
        }

        try {
            // Step 1: Detect scenes using FFmpeg (Chinese-first workflow)
            log.info("Step 1: Detecting scenes with FFmpeg...");
            
            String videoUrl = video.getUrl();
            
            // TODO: Implement Azure Video Indexer or Alibaba Cloud Video AI scene detection
            // Current FFmpeg implementation has been removed
            // See docs/AI_SYSTEM_ARCHITECTURE.md for migration plan
            // Expected API:
            //   List<SceneSegment> sceneSegments = sceneDetectionService.detectScenes(videoUrl, sceneThresholdOverride);
            //
            // For now, create fallback template with single scene
            log.warn("Scene detection not implemented - using fallback template");
            return createFallbackTemplate(video, language);
            
            /* REMOVED FFmpeg scene detection code - TODO: Replace with Azure/Alibaba
            
            List<SceneSegment> sceneSegments = sceneDetectionService.detectScenes(videoUrl, sceneThresholdOverride);
            
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
            String today = java.time.LocalDate.now().toString();
            template.setTemplateTitle((video.getTitle() != null && !video.getTitle().isBlank())
                ? (video.getTitle() + " - AI 模版 " + today)
                : ("AI 模版 " + today));
            template.setScenes(scenes);
            template.setTotalVideoLength(calculateTotalDuration(sceneSegments));
            
            // AI-driven template metadata & per-scene guidance (no presets if AI fails)
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
            **/

        } catch (Exception e) {
            log.error("Error in AI template generation for video ID {}: {}", video.getId(), e.getMessage(), e);
            return createFallbackTemplate(video, language);
        }
    }

    private Scene processScene(SceneSegment segment, int sceneNumber, String videoUrl, String language) {
        // Create base scene with clean data
        Scene scene = new Scene();
        scene.setSceneNumber(sceneNumber);
        scene.setStartTimeMs(segment.getStartTimeMs());
        scene.setEndTimeMs(segment.getEndTimeMs());
        scene.setSceneDurationInSeconds((segment.getEndTimeMs() - segment.getStartTimeMs()) / 1000);
        scene.setSceneSource("ai");
        
        try {
            // Use unified scene analysis service
            log.info("Analyzing scene {} using UnifiedSceneAnalysisService", sceneNumber);
            SceneAnalysisResult analysisResult = unifiedSceneAnalysisService.analyzeScene(
                videoUrl,
                language,
                segment.getStartTime(),
                segment.getEndTime()
            );
            
            // Apply analysis results to scene
            analysisResult.applyToScene(scene);
            
        } catch (Exception e) {
            log.error("Scene analysis failed for scene {}: {} - {}", sceneNumber, e.getClass().getSimpleName(), e.getMessage());
            // Continue with basic scene - will use grid overlay as fallback
        }
        
        // Fallback to grid if no overlays
        if (scene.getOverlayType() == null) {
            scene.setOverlayType("grid");
            scene.setScreenGridOverlay(java.util.List.of(5));
        }
        
        // Build legend if needed
        if (includeLegend && !"grid".equals(scene.getOverlayType()) && overlayLegendService != null) {
            var legend = overlayLegendService.buildLegend(scene, language != null ? language : "zh-CN");
            scene.setLegend(legend);
        }
        
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
        
        // Minimal fallback - leave fields empty so user knows AI failed and can manually edit
        String titleSuffix = "zh".equals(language) || "zh-CN".equals(language) ? " - 基础模板" : " - Basic Template";
        template.setTemplateTitle(video.getTitle() + titleSuffix);
        template.setVideoFormat("1080p 16:9");
        template.setTotalVideoLength(30);

        // Create minimal scene - user will need to fill in details
        Scene defaultScene = new Scene();
        defaultScene.setSceneNumber(1);
        defaultScene.setSceneDurationInSeconds(30);
        // Leave all other fields empty - user will manually edit

        template.setScenes(List.of(defaultScene));
        return template;
    }

    // Configurable threshold (defaults to 0.8 if property is absent)
    private double getRegionsMinConf() {
        try {
            String val = System.getProperty("ai.labeling.regions.minConf");
            if (val == null) {
                val = System.getenv("AI_LABELING_REGIONS_MINCONF");
            }
            if (val != null) {
                return Double.parseDouble(val);
            }
        } catch (Exception ignored) {}
        return 0.8;
    }

    private double clamp01(double v) {
        if (v < 0) return 0; if (v > 1) return 1; return v;
    }

    private String safeEndpoint(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            String host = u.getHost();
            String scheme = u.getScheme();
            if (host == null || scheme == null) return "<invalid-endpoint>";
            return scheme + "://" + host;
        } catch (Exception e) {
            return "<invalid-endpoint>";
        }
    }
    
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
                
                // NEW: Add VL scene analysis for richer context
                if (s.getVlSceneAnalysis() != null && !s.getVlSceneAnalysis().isEmpty()) {
                    so.put("sceneAnalysis", s.getVlSceneAnalysis());
                }
                
                sceneArr.add(so);
            }
            payload.put("scenes", sceneArr);

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
                log.info("[DEBUG] Scene {} BEFORE guidance - vlRawResponse: {}, vlSceneAnalysis: {}", 
                    s.getSceneNumber(),
                    s.getVlRawResponse() != null ? s.getVlRawResponse().length() + " chars" : "null",
                    s.getVlSceneAnalysis() != null ? s.getVlSceneAnalysis().length() + " chars" : "null");
            }
            
            // Ask Qwen (single call) via ObjectLabelService extension
            log.info("[METADATA] Calling objectLabelService.generateTemplateGuidance with payload size: {}", payload.size());
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
                    // NOTE: scriptLine (提词器) is now extracted via ASR/OCR, not from AI
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

    private String trim40(String s) { return s == null ? null : (s.length() > 40 ? s.substring(0,40) : s); }
    private String trim60(String s) { return s == null ? null : (s.length() > 60 ? s.substring(0,60) : s); }

    // These thread-local helpers avoid coupling to DAO-assigned template IDs during generation
    private static final ThreadLocal<String> TL_VIDEO_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> TL_SCENE_NUM = new ThreadLocal<>();
    private String currentVideoIdSafe = TL_VIDEO_ID.get();
    private Integer currentSceneNumberSafe = TL_SCENE_NUM.get();

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


    private String coerceToScriptString(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        if (v instanceof Number n) return String.valueOf(n);
        if (v instanceof java.util.List<?> list) {
            // Join list items into a single short sentence
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item == null) continue;
                String s = null;
                if (item instanceof String si) s = si;
                else if (item instanceof Number ni) s = String.valueOf(ni);
                else if (item instanceof java.util.Map<?,?> mi) s = extractFirstStringFromMap(mi);
                if (s != null && !s.isBlank()) {
                    if (sb.length() > 0) sb.append("，");
                    sb.append(s.trim());
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        if (v instanceof java.util.Map<?,?> m) {
            String s = extractFirstStringFromMap(m);
            return s;
        }
        return null;
    }

    private String extractFirstStringFromMap(java.util.Map<?,?> m) {
        // Common candidate fields
        String[] fields = {"text", "content", "value", "line", "script", "caption"};
        for (String f : fields) {
            Object x = m.get(f);
            if (x instanceof String xs && !xs.isBlank()) return xs;
        }
        // Fallback: first stringy value
        for (Object x : m.values()) {
            if (x instanceof String xs && !xs.isBlank()) return xs;
        }
        return null;
    }
}
// Change Log: Removed block grid services, simplified to use AI object detection only
