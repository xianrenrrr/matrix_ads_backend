package com.example.demo.ai.services;

import com.example.demo.ai.seg.dto.*;
import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.model.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified Scene Analysis Service
 * 
 * Centralizes all scene analysis logic:
 * 1. Keyframe extraction
 * 2. Object detection (YOLO) - optional
 * 3. Qwen VL analysis (labeling + scene description)
 * 
 * Used by:
 * - AI Template Creation (multi-scene)
 * - Manual Template Creation (single scene)
 * - User Video Comparison (with template regions)
 */
@Service
public class UnifiedSceneAnalysisService {
    
    private static final Logger log = LoggerFactory.getLogger(UnifiedSceneAnalysisService.class);
    
    @Autowired
    private KeyframeExtractionService keyframeService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    @Value("${ai.regions.minConf:0.8}")
    private double regionsMinConf;
    
    /**
     * Analyze a scene video with auto-detection
     * 
     * @param videoUrl Video URL to analyze
     * @param language Language for analysis (zh-CN, en, etc.)
     * @param startTime Optional start time for keyframe extraction
     * @param endTime Optional end time for keyframe extraction
     * @return SceneAnalysisResult with VL data
     */
    public SceneAnalysisResult analyzeScene(
        String videoUrl,
        String language,
        Duration startTime,
        Duration endTime
    ) {
        return analyzeScene(videoUrl, null, language, startTime, endTime);
    }
    
    /**
     * Analyze a scene video with provided regions (for comparison)
     * 
     * @param videoUrl Video URL to analyze
     * @param providedRegions Optional regions (null = auto-detect with YOLO)
     * @param language Language for analysis (zh-CN, en, etc.)
     * @param startTime Optional start time for keyframe extraction
     * @param endTime Optional end time for keyframe extraction
     * @return SceneAnalysisResult with VL data
     */
    public SceneAnalysisResult analyzeScene(
        String videoUrl,
        List<Scene.ObjectOverlay> providedRegions,
        String language,
        Duration startTime,
        Duration endTime
    ) {
        return analyzeScene(videoUrl, providedRegions, language, startTime, endTime, null, null);
    }
    
    /**
     * Analyze a scene video with subtitle context (backward compatible)
     * 
     * @param videoUrl Video URL to analyze
     * @param providedRegions Optional regions (null = auto-detect with YOLO)
     * @param language Language for analysis (zh-CN, en, etc.)
     * @param startTime Optional start time for keyframe extraction
     * @param endTime Optional end time for keyframe extraction
     * @param subtitleText Optional subtitle text for this scene (enhances VL analysis)
     * @return SceneAnalysisResult with VL data
     */
    public SceneAnalysisResult analyzeScene(
        String videoUrl,
        List<Scene.ObjectOverlay> providedRegions,
        String language,
        Duration startTime,
        Duration endTime,
        String subtitleText
    ) {
        return analyzeScene(videoUrl, providedRegions, language, startTime, endTime, subtitleText, null);
    }
    
    /**
     * Analyze a scene video with subtitle context and Azure object hints
     * 
     * @param videoUrl Video URL to analyze
     * @param providedRegions Optional regions (null = auto-detect with YOLO)
     * @param language Language for analysis (zh-CN, en, etc.)
     * @param startTime Optional start time for keyframe extraction
     * @param endTime Optional end time for keyframe extraction
     * @param subtitleText Optional subtitle text for this scene (enhances VL analysis)
     * @param azureObjectHints Optional list of object names detected by Azure (for targeted grounding)
     * @return SceneAnalysisResult with VL data
     */
    public SceneAnalysisResult analyzeScene(
        String videoUrl,
        List<Scene.ObjectOverlay> providedRegions,
        String language,
        Duration startTime,
        Duration endTime,
        String subtitleText,
        List<String> azureObjectHints
    ) {
        log.info("[UNIFIED] Analyzing scene: videoUrl={}, hasProvidedRegions={}, language={}, hasSubtitles={}, azureHints={}", 
            videoUrl != null ? videoUrl.substring(0, Math.min(50, videoUrl.length())) + "..." : "null",
            providedRegions != null && !providedRegions.isEmpty(),
            language,
            subtitleText != null && !subtitleText.isEmpty(),
            azureObjectHints != null ? azureObjectHints : "none");
        
        SceneAnalysisResult result = new SceneAnalysisResult();
        
        try {
            // Step 1: Extract keyframe
            String keyframeUrl = keyframeService.extractKeyframe(videoUrl, startTime, endTime);
            result.setKeyframeUrl(keyframeUrl);
            log.info("[UNIFIED] Keyframe extracted: {}", keyframeUrl != null ? "success" : "failed");
            
            if (keyframeUrl == null) {
                log.error("[UNIFIED] Failed to extract keyframe");
                return result;
            }
            
            // Detect aspect ratio from keyframe
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.net.URL(keyframeUrl));
                if (img != null) {
                    int w = img.getWidth();
                    int h = img.getHeight();
                    boolean portrait = h >= w;
                    String aspectRatio = portrait ? "9:16" : "16:9";
                    result.setSourceAspect(aspectRatio);
                    log.info("[UNIFIED] Detected aspect ratio: {} ({}x{})", aspectRatio, w, h);
                }
            } catch (Exception e) {
                log.warn("[UNIFIED] Failed to detect aspect ratio: {}", e.getMessage());
            }
            
            // Step 2: Call Qwen VL for scene analysis with object grounding (bounding boxes)
            log.info("[UNIFIED] Calling Qwen VL for scene analysis with object grounding");
            if (subtitleText != null && !subtitleText.isEmpty()) {
                log.info("[UNIFIED] ✅ Including scriptLine context for keyElements extraction: \"{}\"", 
                    subtitleText.substring(0, Math.min(50, subtitleText.length())) + 
                    (subtitleText.length() > 50 ? "..." : ""));
            } else {
                log.warn("[UNIFIED] ⚠️ No scriptLine context - keyElements extraction will be based on visual only");
            }
            
            if (azureObjectHints != null && !azureObjectHints.isEmpty()) {
                log.info("[UNIFIED] 🎯 Using Azure object hints for targeted grounding: {}", azureObjectHints);
            } else {
                log.info("[UNIFIED] No Azure object hints - Qwen VL will detect objects from scratch");
            }
            
            // Create a dummy full-frame region for Qwen VL (it will detect objects and return bounding boxes)
            List<ObjectLabelService.RegionBox> dummyRegion = new ArrayList<>();
            dummyRegion.add(new ObjectLabelService.RegionBox("full", 0.0, 0.0, 1.0, 1.0));
            
            Map<String, ObjectLabelService.LabelResult> vlResults = new java.util.HashMap<>();
            
            try {
                // Call Qwen VL - it will detect objects and return bounding boxes
                // Pass Azure object hints for targeted grounding
                vlResults = objectLabelService.labelRegions(
                    keyframeUrl, 
                    dummyRegion,
                    language != null ? language : "zh-CN",
                    subtitleText,  // Pass subtitle context to VL
                    azureObjectHints  // Pass Azure detected objects as hints
                );
                
                // Step 4: Extract VL data
                if (!vlResults.isEmpty()) {
                    ObjectLabelService.LabelResult firstResult = vlResults.values().iterator().next();
                    result.setVlRawResponse(firstResult.rawResponse);
                    result.setVlSceneAnalysis(firstResult.sceneAnalysis);
                    
                    // Extract shortLabelZh from VL result if available
                    if (firstResult.labelZh != null && !firstResult.labelZh.isEmpty()) {
                        result.setShortLabelZh(firstResult.labelZh);
                        log.info("[UNIFIED] Extracted shortLabelZh from VL: {}", firstResult.labelZh);
                    }
                    
                    // Extract key elements from VL result
                    if (firstResult.keyElements != null && !firstResult.keyElements.isEmpty()) {
                        result.setKeyElements(firstResult.keyElements);
                        log.info("[UNIFIED] Extracted {} key elements from VL: {}", 
                            firstResult.keyElements.size(), firstResult.keyElements);
                    } else {
                        log.warn("[UNIFIED] No key elements in VL result");
                    }
                    
                    log.info("[UNIFIED] VL analysis complete - rawResponse: {}, sceneAnalysis: {}, keyElements: {}",
                        firstResult.rawResponse != null ? firstResult.rawResponse.length() + " chars" : "null",
                        firstResult.sceneAnalysis != null ? firstResult.sceneAnalysis.length() + " chars" : "null",
                        firstResult.keyElements != null ? firstResult.keyElements.size() : 0);
                } else {
                    log.warn("[UNIFIED] VL returned empty results");
                }
            } catch (Exception vlEx) {
                log.error("[UNIFIED] VL analysis failed: {} - {}", vlEx.getClass().getSimpleName(), vlEx.getMessage());
                // Continue without VL data - will default to grid overlay below
            }
            
            // Step 3: Build overlay objects from Qwen VL bounding boxes
            if (!vlResults.isEmpty()) {
                List<Scene.ObjectOverlay> objects = new ArrayList<>();
                
                for (ObjectLabelService.LabelResult lr : vlResults.values()) {
                    if (lr.box != null && lr.box.length == 4) {
                        // Log raw box values from Qwen VL (0-1000 range)
                        log.info("[UNIFIED-BOX-DEBUG] Raw box from Qwen VL: {} -> [{}, {}, {}, {}]", 
                            lr.labelZh, lr.box[0], lr.box[1], lr.box[2], lr.box[3]);
                        
                        // Convert from 0-1000 range to 0-1 normalized coordinates
                        Scene.ObjectOverlay obj = new Scene.ObjectOverlay();
                        obj.setLabelZh(lr.labelZh != null ? lr.labelZh : "未知");
                        obj.setLabelLocalized(lr.labelZh != null ? lr.labelZh : "未知");
                        obj.setLabel(lr.labelZh != null ? lr.labelZh : "unknown");
                        obj.setConfidence((float) lr.conf);
                        obj.setX((float) lr.box[0] / 1000.0f);
                        obj.setY((float) lr.box[1] / 1000.0f);
                        obj.setWidth((float) lr.box[2] / 1000.0f);
                        obj.setHeight((float) lr.box[3] / 1000.0f);
                        
                        // Log normalized coordinates
                        log.info("[UNIFIED-BOX-DEBUG] Normalized box: {} at [x:{}, y:{}, w:{}, h:{}] (0-1 range)", 
                            lr.labelZh, obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight());
                        
                        // Check if box is too large (>40% of frame in either dimension)
                        if (obj.getWidth() > 0.4f || obj.getHeight() > 0.4f) {
                            log.warn("[UNIFIED-BOX-DEBUG] ⚠️ Large bounding box detected for {}: w={}, h={}", 
                                lr.labelZh, obj.getWidth(), obj.getHeight());
                        }
                        
                        objects.add(obj);
                    }
                }
                
                if (!objects.isEmpty()) {
                    result.setOverlayObjects(objects);
                    result.setOverlayType("objects");
                    result.setShortLabelZh(objects.get(0).getLabelZh());
                    log.info("[UNIFIED] ✅ Built {} overlay objects from Qwen VL bounding boxes", objects.size());
                    for (Scene.ObjectOverlay obj : objects) {
                        log.info("[UNIFIED]    - {} at [{},{},{},{}] conf:{}", 
                            obj.getLabelZh(), obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight(), obj.getConfidence());
                    }
                } else {
                    log.warn("[UNIFIED] ⚠️  No bounding boxes returned by Qwen VL - vlResults had {} entries but no valid boxes", vlResults.size());
                }
            }
            
            // Don't set overlay type if not detected (leave null since we're not using YOLO/grid anymore)
            if (result.getOverlayType() == null) {
                log.info("[UNIFIED] No overlay type set (YOLO disabled, using VL scene analysis only)");
            }
            
            log.info("[UNIFIED] Analysis complete - overlayType: {}", result.getOverlayType());
            
        } catch (Exception e) {
            log.error("[UNIFIED] Analysis failed: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Convert Scene.ObjectOverlay to RegionBox for VL analysis
     */
    private List<ObjectLabelService.RegionBox> convertOverlaysToRegions(List<Scene.ObjectOverlay> overlays) {
        List<ObjectLabelService.RegionBox> regions = new ArrayList<>();
        int counter = 1;
        for (Scene.ObjectOverlay overlay : overlays) {
            String id = "p" + counter++;
            regions.add(new ObjectLabelService.RegionBox(
                id,
                overlay.getX(),
                overlay.getY(),
                overlay.getWidth(),
                overlay.getHeight()
            ));
        }
        return regions;
    }
    
    /**
     * Convert OverlayShape to RegionBox for VL analysis
     */
    private List<ObjectLabelService.RegionBox> convertShapesToRegions(List<OverlayShape> shapes) {
        List<ObjectLabelService.RegionBox> regions = new ArrayList<>();
        int counter = 1;
        
        for (OverlayShape shape : shapes) {
            String id = "p" + counter++;
            double x, y, w, h;
            
            if (shape instanceof OverlayBox b) {
                x = clamp01(b.x());
                y = clamp01(b.y());
                w = clamp01(b.w());
                h = clamp01(b.h());
            } else if (shape instanceof OverlayPolygon p) {
                // Calculate bounding box from polygon
                double minX = 1.0, minY = 1.0, maxX = 0.0, maxY = 0.0;
                for (com.example.demo.ai.seg.dto.Point pt : p.points()) {
                    minX = Math.min(minX, pt.x());
                    minY = Math.min(minY, pt.y());
                    maxX = Math.max(maxX, pt.x());
                    maxY = Math.max(maxY, pt.y());
                }
                x = clamp01(minX);
                y = clamp01(minY);
                w = clamp01(maxX - minX);
                h = clamp01(maxY - minY);
            } else {
                continue;
            }
            
            regions.add(new ObjectLabelService.RegionBox(id, x, y, w, h));
        }
        
        return regions;
    }
    
    /**
     * Build overlay objects and polygons from detected shapes and VL results
     */
    private void buildOverlays(
        SceneAnalysisResult result,
        List<OverlayShape> shapes,
        Map<String, ObjectLabelService.LabelResult> vlResults
    ) {
        List<Scene.ObjectOverlay> objects = new ArrayList<>();
        List<com.example.demo.ai.seg.dto.OverlayPolygonClass> polygons = new ArrayList<>();
        
        boolean hasPolygons = false;
        boolean hasBoxes = false;
        
        // Map shapes to IDs
        Map<OverlayShape, String> idMap = new java.util.HashMap<>();
        int counter = 1;
        for (OverlayShape shape : shapes) {
            idMap.put(shape, "p" + counter++);
        }
        
        // Process each shape
        for (OverlayShape shape : shapes) {
            String id = idMap.get(shape);
            String labelZh = "未知";
            
            // Get label from VL results
            if (vlResults.containsKey(id)) {
                ObjectLabelService.LabelResult lr = vlResults.get(id);
                if (lr != null && lr.labelZh != null && !lr.labelZh.isBlank() && lr.conf >= regionsMinConf) {
                    labelZh = lr.labelZh;
                }
            }
            
            // Create overlay based on shape type
            if (shape instanceof OverlayPolygon polygon) {
                hasPolygons = true;
                // Create OverlayPolygonClass
                com.example.demo.ai.seg.dto.OverlayPolygonClass scenePolygon = 
                    new com.example.demo.ai.seg.dto.OverlayPolygonClass();
                scenePolygon.setLabel(polygon.label());
                scenePolygon.setLabelZh(labelZh);
                scenePolygon.setLabelLocalized(labelZh);
                scenePolygon.setConfidence((float) polygon.confidence());
                
                // Convert points
                List<com.example.demo.ai.seg.dto.OverlayPolygonClass.Point> scenePoints = new ArrayList<>();
                for (com.example.demo.ai.seg.dto.Point p : polygon.points()) {
                    scenePoints.add(new com.example.demo.ai.seg.dto.OverlayPolygonClass.Point(
                        (float) p.x(), (float) p.y()
                    ));
                }
                scenePolygon.setPoints(scenePoints);
                polygons.add(scenePolygon);
                
            } else if (shape instanceof OverlayBox box) {
                hasBoxes = true;
                Scene.ObjectOverlay obj = new Scene.ObjectOverlay();
                obj.setLabel(box.label());
                obj.setLabelZh(labelZh);
                obj.setLabelLocalized(labelZh);
                obj.setConfidence((float) box.confidence());
                obj.setX((float) box.x());
                obj.setY((float) box.y());
                obj.setWidth((float) box.w());
                obj.setHeight((float) box.h());
                objects.add(obj);
            }
        }
        
        // Set results
        if (hasPolygons) {
            result.setOverlayPolygons(polygons);
            result.setOverlayType("polygons");
            if (!polygons.isEmpty()) {
                result.setShortLabelZh(polygons.get(0).getLabelZh());
            }
        } else if (hasBoxes) {
            result.setOverlayObjects(objects);
            result.setOverlayType("objects");
            if (!objects.isEmpty()) {
                result.setShortLabelZh(objects.get(0).getLabelZh());
            }
        } else {
            result.setOverlayType("grid");
        }
    }
    
    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
