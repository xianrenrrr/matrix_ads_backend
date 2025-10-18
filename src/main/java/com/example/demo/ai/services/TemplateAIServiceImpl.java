package com.example.demo.ai.services;

import com.example.demo.ai.providers.vision.FFmpegSceneDetectionService;
import com.example.demo.ai.providers.llm.VideoSummaryService;
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
    
    @Autowired
    private VideoMetadataService videoMetadataService;
    
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
            
            // Use FFmpeg for scene detection instead of Google Video Intelligence
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
            log.info("=== AI TEMPLATE GUIDANCE GENERATION ===");
            generateAIMetadata(template, video, scenes, allSceneLabels, language, userDescription);            
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
                    log.info("[VL] About to call labelRegions with {} regions", regions.size());
                    regionLabels = objectLabelService.labelRegions(keyframeUrl, regions, language != null ? language : "zh-CN");
                    log.info("[VL] labelRegions returned {} results", regionLabels.size());
                    
                    // NEW: Save VL scene analysis and raw response to scene
                    if (!regionLabels.isEmpty()) {
                        com.example.demo.ai.label.ObjectLabelService.LabelResult firstResult = regionLabels.values().iterator().next();
                        log.info("[VL] First result - sceneAnalysis: {}, rawResponse: {}", 
                            firstResult.sceneAnalysis != null ? firstResult.sceneAnalysis.length() + " chars" : "null",
                            firstResult.rawResponse != null ? firstResult.rawResponse.length() + " chars" : "null");
                        
                        if (firstResult.sceneAnalysis != null && !firstResult.sceneAnalysis.isEmpty()) {
                            scene.setVlSceneAnalysis(firstResult.sceneAnalysis);
                            log.info("[VL] Scene analysis saved ({} chars)", firstResult.sceneAnalysis.length());
                        }
                        if (firstResult.rawResponse != null && !firstResult.rawResponse.isEmpty()) {
                            scene.setVlRawResponse(firstResult.rawResponse);
                            log.info("[VL] Raw response saved ({} chars)", firstResult.rawResponse.length());
                        }
                    } else {
                        log.warn("[VL] regionLabels is empty, cannot save VL data");
                    }
                } catch (Exception e) {
                    log.error("[VL] Exception while saving VL data: {}", e.getMessage(), e);
                }

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
                if (includeLegend && !"grid".equals(scene.getOverlayType()) && overlayLegendService != null) {
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
                boolean yoloApplied = applyHuggingFaceYoloFallback(scene, keyframeUrl, language);
                if (!yoloApplied) {
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
        String zh = box.labelZh();
        String lbl = box.label();
        if (zh != null && !zh.isBlank()) {
            model.setLabelZh(zh);
            model.setLabelLocalized(zh);
        }
        if (lbl == null || lbl.isBlank()) lbl = zh; // fall back to zh to avoid empty label
        model.setLabel(lbl);
        if (model.getLabelLocalized() == null || model.getLabelLocalized().isBlank()) {
            model.setLabelLocalized(lbl);
        }
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

    private boolean applyHuggingFaceYoloFallback(Scene scene, String keyframeUrl, String language) {
        if (!yoloFallbackEnabled || hfYoloEndpoint == null || hfYoloEndpoint.isBlank()) return false;
        try {
            log.info("[HF-YOLO] fallback enabled; endpoint host={}", safeEndpoint(hfYoloEndpoint));
            // Download keyframe to compute image dimensions and for HF binary post
            BufferedImage img = ImageIO.read(new URL(keyframeUrl));
            if (img == null) return false;
            int width = img.getWidth();
            int height = img.getHeight();

            // Encode as JPEG bytes
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // Call HuggingFace Inference API
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "image/jpeg");
            if (hfYoloApiKey != null && !hfYoloApiKey.isBlank()) headers.set("Authorization", "Bearer " + hfYoloApiKey);
            org.springframework.http.HttpEntity<byte[]> req = new org.springframework.http.HttpEntity<>(imageBytes, headers);
            org.springframework.http.ResponseEntity<String> resp = rt.postForEntity(hfYoloEndpoint, req, String.class);
            log.info("[HF-YOLO] status={} bodyLen={}", resp.getStatusCodeValue(), resp.getBody() == null ? 0 : resp.getBody().length());
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().isBlank()) return false;

            // Parse HF response
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> dets;
            try {
                dets = mapper.readValue(resp.getBody(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>(){});
            } catch (Exception e) {
                log.warn("[HF-YOLO] parse error: {}", e.toString());
                return false;
            }
            if (dets.isEmpty()) { log.info("[HF-YOLO] no detections"); return false; }

            // Convert to normalized boxes and post-process
            java.util.List<com.example.demo.model.Scene.ObjectOverlay> boxes = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> d : dets) {
                double score = ((Number) d.getOrDefault("score", 0.0)).doubleValue();
                @SuppressWarnings("unchecked") java.util.Map<String, Object> box = (java.util.Map<String, Object>) d.get("box");
                if (box == null) continue;
                double xmin = ((Number) box.getOrDefault("xmin", 0)).doubleValue();
                double ymin = ((Number) box.getOrDefault("ymin", 0)).doubleValue();
                double xmax = ((Number) box.getOrDefault("xmax", 0)).doubleValue();
                double ymax = ((Number) box.getOrDefault("ymax", 0)).doubleValue();
                double x = xmin / width;
                double y = ymin / height;
                double w = Math.max(0, xmax - xmin) / width;
                double h = Math.max(0, ymax - ymin) / height;
                double area = w * h;
                if (area < hfMinArea) continue;
                com.example.demo.model.Scene.ObjectOverlay m = new com.example.demo.model.Scene.ObjectOverlay();
                m.setLabel((String) d.getOrDefault("label", ""));
                m.setConfidence((float) score);
                m.setX((float) x); m.setY((float) y); m.setWidth((float) w); m.setHeight((float) h);
                boxes.add(m);
            }
            if (boxes.isEmpty()) { log.info("[HF-YOLO] no boxes after filter"); return false; }

            // Keep top-K by score*area
            boxes.sort((a,b) -> Double.compare(
                ((double) b.getConfidence()) * b.getWidth() * b.getHeight(),
                ((double) a.getConfidence()) * a.getWidth() * a.getHeight()));
            if (boxes.size() > hfMaxObjects) boxes = boxes.subList(0, hfMaxObjects);

            // Build regions p1..pn and label via Qwen
            java.util.List<com.example.demo.ai.label.ObjectLabelService.RegionBox> regions = new java.util.ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                var b = boxes.get(i);
                regions.add(new com.example.demo.ai.label.ObjectLabelService.RegionBox("p" + (i+1), b.getX(), b.getY(), b.getWidth(), b.getHeight()));
            }
            java.util.Map<String, com.example.demo.ai.label.ObjectLabelService.LabelResult> regionLabels = java.util.Collections.emptyMap();
            try {
                regionLabels = objectLabelService.labelRegions(keyframeUrl, regions, language != null ? language : "zh-CN");
            } catch (Exception ignore) {}

            // Apply labels
            for (int i = 0; i < boxes.size(); i++) {
                var b = boxes.get(i);
                String sid = "p" + (i+1);
                String zh = null; double conf = 0.0;
                if (regionLabels != null && regionLabels.containsKey(sid)) {
                    var lr = regionLabels.get(sid);
                    if (lr != null && lr.labelZh != null && !lr.labelZh.isBlank() && lr.conf >= getRegionsMinConf()) {
                        zh = lr.labelZh; conf = lr.conf;
                    }
                }
                if (zh == null) zh = "未知";
                b.setLabelZh(zh);
                b.setLabelLocalized(zh);
                if (b.getLabel() == null || b.getLabel().isBlank()) b.setLabel(zh);
            }

            scene.setOverlayObjects(boxes);
            scene.setOverlayType("objects");
            if (includeLegend && overlayLegendService != null) {
                var legend = overlayLegendService.buildLegend(scene, language != null ? language : "zh-CN");
                scene.setLegend(legend);
            }
            log.info("[HF-YOLO] applied {} boxes", boxes.size());
            return true;
        } catch (Exception e) {
            log.warn("[HF-YOLO] error: {}", e.toString());
            return false;
        }
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
                    // Parse script line more robustly (string/list/map and fallback keys)
                    String scriptLine = parseScriptLineFromGuidance(one);
                    if (scriptLine != null && !scriptLine.isBlank()) {
                        s.setScriptLine(scriptLine);
                    }
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

    private String sanitizeAndClampScript(String s) {
        if (s == null) return null;
        String original = s;
        // Strip code fences and markdown artifacts
        s = s.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1");
        s = s.replaceAll("(?s)```\\s*(.*?)\\s*```", "$1");
        // Collapse whitespace/newlines
        s = s.replaceAll("\n|\r", " ").replaceAll("\\s+", " ").trim();
        // Normalize common ASCII punctuation to Chinese style for zh content
        s = s.replace(',', '，')
             .replace(':', '：')
             .replace(';', '；');
        // Convert terminal punctuation to Chinese variants if applicable
        if (s.endsWith(".")) s = s.substring(0, s.length()-1) + "。";
        if (s.endsWith("!")) s = s.substring(0, s.length()-1) + "！";
        if (s.endsWith("?")) s = s.substring(0, s.length()-1) + "？";
        // Ensure ends with a sentence terminator (avoid dangling comma)
        if (!s.isEmpty() && !s.matches(".*[。！？！？]$")) {
            s = s + "。";
        }
        // Optionally clamp to 40 chars
        if (enforceScriptMaxLen && s.length() > 40) {
            String clamped = s.substring(0, 40);
            log.info("[GUIDANCE] scriptLine clamped videoId={} scene={} oldLen={} newLen=40", 
                (Object) currentVideoIdSafe, (Object) currentSceneNumberSafe, original.length());
            return clamped;
        }
        return s;
    }

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

    // --- Helpers for robust script parsing ---
    private String parseScriptLineFromGuidance(Map<String, Object> one) {
        // Primary key
        Object script = one.get("scriptLine");
        String parsed = coerceToScriptString(script);
        if (parsed != null && !parsed.isBlank()) return sanitizeAndClampScript(parsed);
        // Fallback keys commonly returned by LLMs
        String[] keys = {"script", "subtitle", "dialogue", "caption", "line"};
        for (String k : keys) {
            Object v = one.get(k);
            parsed = coerceToScriptString(v);
            if (parsed != null && !parsed.isBlank()) return sanitizeAndClampScript(parsed);
        }
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
