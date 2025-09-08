package com.example.demo.ai.services;

import com.example.demo.ai.providers.vision.FFmpegSceneDetectionService;
import com.example.demo.ai.providers.llm.VideoSummaryService;
import com.example.demo.ai.providers.vision.GoogleVisionProvider;
import com.example.demo.ai.providers.vision.VisionProvider;
import com.example.demo.ai.core.AIResponse;
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

    @Autowired
    private FFmpegSceneDetectionService sceneDetectionService;
    
    @Autowired
    private SegmentationService segmentationService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    
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

    @Autowired(required = false)
    private GoogleVisionProvider googleVisionProvider;

    @Value("${ai.overlay.visionFallback.enabled:true}")
    private boolean visionFallbackEnabled;

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
            String today = java.time.LocalDate.now().toString();
            template.setTemplateTitle((video.getTitle() != null && !video.getTitle().isBlank())
                ? (video.getTitle() + " - AI 模版 " + today)
                : ("AI 模版 " + today));
            template.setScenes(scenes);
            
            // Set some default values (note: these are hardcoded and not AI-generated)
            template.setVideoFormat("1080p 16:9");
            template.setTotalVideoLength(calculateTotalDuration(sceneSegments));
            
            // AI-driven template metadata & per-scene guidance (no presets if AI fails)
            log.info("=== AI TEMPLATE GUIDANCE GENERATION ===");
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
        
        // Removed AI orchestrator fallback; if no overlays after segmentation, we'll default to grid.
        if (scene.getOverlayType() == null) {
            scene.setOverlayType("grid");
            scene.setScreenGridOverlay(java.util.List.of(5));
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
                
                // Build region list (one-shot labeling)
                java.util.Map<OverlayShape, String> idMap = new java.util.HashMap<>();
                java.util.List<com.example.demo.ai.label.ObjectLabelService.RegionBox> regions = new java.util.ArrayList<>();
                int idCounter = 1;
                for (OverlayShape s : shapes) {
                    String id = "p" + (idCounter++);
                    idMap.put(s, id);
                    double x, y, w, h;
                    if (s instanceof OverlayBox b) {
                        x = clamp01(b.x()); y = clamp01(b.y()); w = clamp01(b.w()); h = clamp01(b.h());
                    } else if (s instanceof OverlayPolygon p) {
                        double minX = 1.0, minY = 1.0, maxX = 0.0, maxY = 0.0;
                        for (com.example.demo.ai.seg.dto.Point pt : p.points()) {
                            minX = Math.min(minX, pt.x()); minY = Math.min(minY, pt.y());
                            maxX = Math.max(maxX, pt.x()); maxY = Math.max(maxY, pt.y());
                        }
                        x = clamp01(minX); y = clamp01(minY); w = clamp01(maxX - minX); h = clamp01(maxY - minY);
                    } else { continue; }
                    regions.add(new com.example.demo.ai.label.ObjectLabelService.RegionBox(id, x, y, w, h));
                }

                java.util.Map<String, com.example.demo.ai.label.ObjectLabelService.LabelResult> regionLabels = java.util.Collections.emptyMap();
                try {
                    regionLabels = objectLabelService.labelRegions(keyframeUrl, regions, language != null ? language : "zh-CN");
                } catch (Exception ignore) {}

                for (OverlayShape shape : shapes) {
                    String labelZh = null;
                    String sid = idMap.get(shape);
                    if (sid != null && regionLabels.containsKey(sid)) {
                        var lr = regionLabels.get(sid);
                        // Threshold controlled by property, default 0.8
                        if (lr != null && lr.labelZh != null && !lr.labelZh.isBlank() && lr.conf >= getRegionsMinConf()) {
                            labelZh = lr.labelZh;
                        }
                    }
                    if (labelZh == null) labelZh = "未知";
                    
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

                // Set dominant object's Chinese label as scene's short label (no recrop)
                if (!shapes.isEmpty()) {
                    OverlayShape dominant = shapes.get(0);
                    String dom = null;
                    if (dominant instanceof OverlayBox db) dom = db.labelZh();
                    else if (dominant instanceof OverlayPolygon dp) dom = dp.labelZh();
                    scene.setShortLabelZh(dom);
                }
            } else {
                // No shapes detected by primary segmentation – try Google Vision polygon fallback
                boolean visionApplied = false;
                if (visionFallbackEnabled && googleVisionProvider != null) {
                    try {
                        AIResponse<java.util.List<VisionProvider.ObjectPolygon>> resp = googleVisionProvider.detectObjectPolygons(keyframeUrl);
                        if (resp != null && resp.isSuccess() && resp.getData() != null && !resp.getData().isEmpty()) {
                            // Convert to legacy polygon DTOs used by Scene
                            java.util.List<GoogleVisionProvider.OverlayPolygon> legacy = new java.util.ArrayList<>();

                            // Prepare region boxes for one-shot Chinese labeling
                            java.util.List<com.example.demo.ai.label.ObjectLabelService.RegionBox> regions = new java.util.ArrayList<>();
                            int idCounter = 1;
                            for (VisionProvider.ObjectPolygon vp : resp.getData()) {
                                // Convert points
                                java.util.List<GoogleVisionProvider.OverlayPolygon.Point> pts = new java.util.ArrayList<>();
                                double minX = 1.0, minY = 1.0, maxX = 0.0, maxY = 0.0;
                                for (VisionProvider.ObjectPolygon.Point p : vp.getPoints()) {
                                    float x = p.getX();
                                    float y = p.getY();
                                    pts.add(new GoogleVisionProvider.OverlayPolygon.Point(x, y));
                                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                                }
                                GoogleVisionProvider.OverlayPolygon lp = new GoogleVisionProvider.OverlayPolygon(vp.getLabel(), vp.getConfidence(), pts);
                                legacy.add(lp);

                                // Region box for labeling
                                String id = "p" + (idCounter++);
                                double bx = clamp01(minX), by = clamp01(minY), bw = clamp01(maxX - minX), bh = clamp01(maxY - minY);
                                regions.add(new com.example.demo.ai.label.ObjectLabelService.RegionBox(id, bx, by, bw, bh));
                            }

                            // Batch label regions in Chinese
                            java.util.Map<String, com.example.demo.ai.label.ObjectLabelService.LabelResult> regionLabels = java.util.Collections.emptyMap();
                            try {
                                // Build id map in the same order
                                java.util.Map<Integer, String> indexId = new java.util.HashMap<>();
                                for (int i = 0; i < regions.size(); i++) indexId.put(i, "p" + (i + 1));
                                regionLabels = objectLabelService.labelRegions(keyframeUrl, regions, language != null ? language : "zh-CN");
                            } catch (Exception ignore) {}

                            // Apply labels to polygons
                            for (int i = 0; i < legacy.size(); i++) {
                                GoogleVisionProvider.OverlayPolygon lp = legacy.get(i);
                                String sid = "p" + (i + 1);
                                String zh = null; double conf = 0.0;
                                if (regionLabels != null && regionLabels.containsKey(sid)) {
                                    var lr = regionLabels.get(sid);
                                    if (lr != null && lr.labelZh != null && !lr.labelZh.isBlank() && lr.conf >= getRegionsMinConf()) {
                                        zh = lr.labelZh; conf = lr.conf;
                                    }
                                }
                                if (zh == null) zh = "未知";
                                lp.setLabelZh(zh);
                                lp.setLabelLocalized(zh);
                            }

                            scene.setOverlayPolygons(legacy);
                            scene.setOverlayType("polygons");
                            // Legend
                            if (overlayLegendService != null) {
                                var legend = overlayLegendService.buildLegend(scene, language != null ? language : "zh-CN");
                                scene.setLegend(legend);
                            }
                            visionApplied = true;
                        }
                    } catch (Exception ignore) {}
                }

                if (!visionApplied) {
                    // Fallback to grid overlay
                    scene.setOverlayType("grid");
                    scene.setScreenGridOverlay(java.util.List.of(5));
                }
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
    
    private void generateAIMetadata(ManualTemplate template, Video video, List<Scene> scenes, 
                                   List<String> sceneLabels, String language, String userDescription) {
        try {
            // Build compact payload for one-shot Chinese guidance
            Map<String, Object> payload = new java.util.HashMap<>();
            Map<String, Object> tpl = new java.util.HashMap<>();
            tpl.put("videoTitle", video.getTitle());
            tpl.put("language", language);
            tpl.put("totalDurationSeconds", template.getTotalVideoLength());
            tpl.put("videoFormat", template.getVideoFormat());
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
                sceneArr.add(so);
            }
            payload.put("scenes", sceneArr);

            // Ask Qwen (single call) via ObjectLabelService extension
            Map<String, Object> result = objectLabelService.generateTemplateGuidance(payload);
            if (result == null || result.isEmpty()) {
                log.info("AI guidance unavailable; leaving metadata/guidance empty (no presets)");
                return;
            }

            // Apply template metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> t = (Map<String, Object>) result.get("template");
            if (t != null) {
                Object vp = t.get("videoPurpose"); if (vp instanceof String s) template.setVideoPurpose(trim40(s));
                Object tone = t.get("tone"); if (tone instanceof String s) template.setTone(trim40(s));
                Object light = t.get("lightingRequirements"); if (light instanceof String s) template.setLightingRequirements(trim60(s));
                Object bgm = t.get("backgroundMusic"); if (bgm instanceof String s) template.setBackgroundMusic(trim40(s));
            }

            // Apply per-scene guidance
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> rs = (java.util.List<Map<String, Object>>) result.get("scenes");
            if (rs != null) {
                java.util.Map<Integer, Map<String, Object>> byNum = new java.util.HashMap<>();
                for (Map<String, Object> one : rs) {
                    Object n = one.get("sceneNumber");
                    if (n instanceof Number num) byNum.put(num.intValue(), one);
                }
                for (Scene s : scenes) {
                    Map<String, Object> one = byNum.get(s.getSceneNumber());
                    if (one == null) continue;
                    Object script = one.get("scriptLine"); if (script instanceof String v) s.setScriptLine(trim60(v));
                    Object person = one.get("presenceOfPerson"); if (person instanceof Boolean b) s.setPresenceOfPerson(b);
                    Object move = one.get("movementInstructions"); if (move instanceof String v) s.setMovementInstructions(trim60(v));
                    Object bg = one.get("backgroundInstructions"); if (bg instanceof String v) s.setBackgroundInstructions(trim60(v));
                    Object cam = one.get("specificCameraInstructions"); if (cam instanceof String v) s.setSpecificCameraInstructions(trim60(v));
                    Object audio = one.get("audioNotes"); if (audio instanceof String v) s.setAudioNotes(trim60(v));
                }
            }
            // Device orientation: derive from keyframe and apply to all scenes (independent of AI)
            String orientationZh = deriveDeviceOrientationFromFirstScene(scenes, language);
            if (orientationZh != null) {
                for (Scene s : scenes) {
                    s.setDeviceOrientation(orientationZh);
                }
            }
        } catch (Exception e) {
            log.info("AI guidance generation failed: {}. Leaving fields empty.", e.getMessage());
            // Still derive and set device orientation even if AI guidance failed
            String orientationZh = deriveDeviceOrientationFromFirstScene(scenes, language);
            if (orientationZh != null) {
                for (Scene s : scenes) {
                    s.setDeviceOrientation(orientationZh);
                }
            }
        }
    }

    private String trim40(String s) { return s == null ? null : (s.length() > 40 ? s.substring(0,40) : s); }
    private String trim60(String s) { return s == null ? null : (s.length() > 60 ? s.substring(0,60) : s); }

    private String deriveDeviceOrientationFromFirstScene(List<Scene> scenes, String language) {
        try {
            for (Scene s : scenes) {
                if (s.getKeyframeUrl() == null || s.getKeyframeUrl().isBlank()) continue;
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.net.URL(s.getKeyframeUrl()));
                if (img == null) continue;
                int w = img.getWidth(), h = img.getHeight();
                boolean portrait = h >= w;
                boolean zh = "zh".equalsIgnoreCase(language) || "zh-CN".equalsIgnoreCase(language);
                if (portrait) return zh ? "手机（竖屏 9:16）" : "Phone (Portrait 9:16)";
                return zh ? "手机（横屏 16:9）" : "Phone (Landscape 16:9)";
            }
        } catch (Exception ignored) {}
        return null;
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
