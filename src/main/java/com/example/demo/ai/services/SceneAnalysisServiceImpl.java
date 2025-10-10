package com.example.demo.ai.services;

import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.*;
import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.model.Scene;
import com.example.demo.model.Video;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of SceneAnalysisService that reuses AI analysis logic
 * from TemplateAIServiceImpl but without scene detection.
 * 
 * NOTE: This service extracts and reuses core AI analysis logic to avoid code duplication.
 * Consider refactoring TemplateAIServiceImpl to use this service internally in the future.
 */
@Service
public class SceneAnalysisServiceImpl implements SceneAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SceneAnalysisServiceImpl.class);

    @Autowired
    private SegmentationService segmentationService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    @Autowired
    private OverlayLegendService overlayLegendService;
    
    @Autowired
    private KeyframeExtractionService keyframeExtractionService;
    
    @Autowired
    private VideoDurationService videoDurationService;
    
    @Autowired
    private SceneInstructionService sceneInstructionService;
    
    @Autowired
    private ShapeProcessingUtil shapeProcessingUtil;
    
    @Value("${ai.overlay.includeLegend:false}")
    private boolean includeLegend;
    
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

    @Override
    public Scene analyzeSingleScene(Video video, String language, String sceneDescription) {
        log.info("Analyzing single scene video: {} with description: {}", 
                 video.getId(), sceneDescription != null ? sceneDescription : "none");
        
        Scene scene = new Scene();
        
        try {
            // 1. Get video duration using FFmpeg
            Double durationSeconds = videoDurationService.getVideoDuration(video.getUrl());
            if (durationSeconds == null) {
                log.warn("Could not extract video duration, using default 5 seconds");
                durationSeconds = 5.0;
            }
            
            long durationMs = (long) (durationSeconds * 1000);
            scene.setStartTimeMs(0L);
            scene.setEndTimeMs(durationMs);
            scene.setSceneDurationInSeconds((long) Math.ceil(durationSeconds));
            
            // 2. Extract keyframe from middle of video
            String keyframeUrl = extractKeyframeFromVideo(video.getUrl(), video.getId());
            if (keyframeUrl != null) {
                scene.setKeyframeUrl(keyframeUrl);
                
                // 3. Process scene with AI shape detection (REUSED from TemplateAIServiceImpl)
                processSceneWithShapes(scene, keyframeUrl, language);
            }
            
            // 4. Fallback to grid if no overlays detected
            if (scene.getOverlayType() == null) {
                scene.setOverlayType("grid");
                scene.setScreenGridOverlay(List.of(5)); // Center grid
                scene.setScreenGridOverlayLabels(List.of("主要内容"));
            }
            
            // 5. Generate AI instructions using scene description and detected objects
            sceneInstructionService.generateInstructions(scene, sceneDescription, language);
            
            // 6. Set metadata
            scene.setSceneSource("manual");
            
            log.info("Scene analysis completed for video: {}", video.getId());
            return scene;
            
        } catch (Exception e) {
            log.error("Error analyzing scene video {}: {}", video.getId(), e.getMessage(), e);
            // Return basic scene with grid overlay
            scene.setOverlayType("grid");
            scene.setScreenGridOverlay(List.of(5));
            scene.setSceneSource("manual");
            return scene;
        }
    }
    
    /**
     * Extract keyframe from video (REUSED logic from TemplateAIServiceImpl)
     * NOTE: For single scene videos, we extract from the middle of the video
     */
    private String extractKeyframeFromVideo(String videoUrl, String videoId) {
        try {
            log.info("Extracting keyframe from video: {}", videoId);
            // Extract from middle of video (start=0s, end=10s as default, will extract at 5s)
            java.time.Duration startTime = java.time.Duration.ofSeconds(0);
            java.time.Duration endTime = java.time.Duration.ofSeconds(10);
            return keyframeExtractionService.extractKeyframe(videoUrl, startTime, endTime);
        } catch (Exception e) {
            log.error("Keyframe extraction failed for video {}: {}", videoId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Process scene with shape detection using shared utility
     */
    private void processSceneWithShapes(Scene scene, String keyframeUrl, String language) {
        try {
            // Detect shapes using segmentation service
            List<OverlayShape> shapes = segmentationService.detect(keyframeUrl);
            
            if (!shapes.isEmpty()) {
                // Use shared utility to process shapes and add labels
                boolean shapesProcessed = shapeProcessingUtil.processShapesWithLabels(
                    scene, shapes, keyframeUrl, objectLabelService, language
                );
                
                // Build legend if needed
                if (shapesProcessed && includeLegend && !"grid".equals(scene.getOverlayType()) && overlayLegendService != null) {
                    var legend = overlayLegendService.buildLegend(scene, language != null ? language : "zh-CN");
                    scene.setLegend(legend);
                }
                
            } else {
                // Try YOLO fallback if no shapes detected
                boolean yoloApplied = applyHuggingFaceYoloFallback(scene, keyframeUrl, language);
                if (!yoloApplied) {
                    scene.setOverlayType("grid");
                    scene.setScreenGridOverlay(List.of(5));
                }
            }
        } catch (Exception e) {
            log.error("Failed to process scene with shapes: {}", e.getMessage());
            scene.setOverlayType("grid");
            scene.setScreenGridOverlay(List.of(5));
        }
    }
    
    /**
     * YOLO fallback (REUSED from TemplateAIServiceImpl)
     * NOTE: This is duplicated code - consider refactoring to shared utility class
     */
    private boolean applyHuggingFaceYoloFallback(Scene scene, String keyframeUrl, String language) {
        if (!yoloFallbackEnabled || hfYoloEndpoint == null || hfYoloEndpoint.isBlank()) {
            return false;
        }
        
        try {
            log.info("[HF-YOLO] Applying fallback for scene analysis");
            
            // Download keyframe
            BufferedImage img = ImageIO.read(new URL(keyframeUrl));
            if (img == null) return false;
            
            int width = img.getWidth();
            int height = img.getHeight();

            // Encode as JPEG
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // Call HuggingFace API
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "image/jpeg");
            if (hfYoloApiKey != null && !hfYoloApiKey.isBlank()) {
                headers.set("Authorization", "Bearer " + hfYoloApiKey);
            }
            
            org.springframework.http.HttpEntity<byte[]> req = new org.springframework.http.HttpEntity<>(imageBytes, headers);
            org.springframework.http.ResponseEntity<String> resp = rt.postForEntity(hfYoloEndpoint, req, String.class);
            
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().isBlank()) {
                return false;
            }

            // Parse response
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> dets = mapper.readValue(
                resp.getBody(), 
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>(){}
            );
            
            if (dets.isEmpty()) {
                return false;
            }

            // Convert to normalized boxes
            List<Scene.ObjectOverlay> boxes = new ArrayList<>();
            for (Map<String, Object> d : dets) {
                double score = ((Number) d.getOrDefault("score", 0.0)).doubleValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> box = (Map<String, Object>) d.get("box");
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
                
                Scene.ObjectOverlay m = new Scene.ObjectOverlay();
                m.setLabel((String) d.getOrDefault("label", ""));
                m.setConfidence((float) score);
                m.setX((float) x);
                m.setY((float) y);
                m.setWidth((float) w);
                m.setHeight((float) h);
                boxes.add(m);
            }
            
            if (boxes.isEmpty()) {
                return false;
            }

            // Keep top-K by score*area
            boxes.sort((a, b) -> Double.compare(
                ((double) b.getConfidence()) * b.getWidth() * b.getHeight(),
                ((double) a.getConfidence()) * a.getWidth() * a.getHeight()
            ));
            
            if (boxes.size() > hfMaxObjects) {
                boxes = boxes.subList(0, hfMaxObjects);
            }

            // Label regions with Chinese
            List<ObjectLabelService.RegionBox> regions = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                var b = boxes.get(i);
                regions.add(new ObjectLabelService.RegionBox(
                    "p" + (i + 1), b.getX(), b.getY(), b.getWidth(), b.getHeight()
                ));
            }
            
            Map<String, ObjectLabelService.LabelResult> regionLabels = new HashMap<>();
            try {
                regionLabels = objectLabelService.labelRegions(
                    keyframeUrl, regions, language != null ? language : "zh-CN"
                );
            } catch (Exception ignore) {}

            // Apply labels
            for (int i = 0; i < boxes.size(); i++) {
                var b = boxes.get(i);
                String sid = "p" + (i + 1);
                String zh = null;
                
                if (regionLabels.containsKey(sid)) {
                    var lr = regionLabels.get(sid);
                    if (lr != null && lr.labelZh != null && !lr.labelZh.isBlank() && lr.conf >= 0.8) {
                        zh = lr.labelZh;
                    }
                }
                
                if (zh == null) zh = "未知";
                b.setLabelZh(zh);
                b.setLabelLocalized(zh);
                if (b.getLabel() == null || b.getLabel().isBlank()) {
                    b.setLabel(zh);
                }
            }

            scene.setOverlayObjects(boxes);
            scene.setOverlayType("objects");
            
            if (includeLegend && overlayLegendService != null) {
                var legend = overlayLegendService.buildLegend(scene, language != null ? language : "zh-CN");
                scene.setLegend(legend);
            }
            
            log.info("[HF-YOLO] Applied {} boxes", boxes.size());
            return true;
            
        } catch (Exception e) {
            log.warn("[HF-YOLO] Error: {}", e.toString());
            return false;
        }
    }
}
