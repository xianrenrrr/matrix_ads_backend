package com.example.demo.ai.services;

import com.example.demo.ai.seg.SegmentationService;
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
    private SegmentationService segmentationService;
    
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
        log.info("[UNIFIED] Analyzing scene: videoUrl={}, hasProvidedRegions={}, language={}", 
            videoUrl != null ? videoUrl.substring(0, Math.min(50, videoUrl.length())) + "..." : "null",
            providedRegions != null && !providedRegions.isEmpty(),
            language);
        
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
            
            // Step 2: Get regions (either provided or auto-detect)
            List<ObjectLabelService.RegionBox> regions = new ArrayList<>();
            List<OverlayShape> detectedShapes = new ArrayList<>();
            
            if (providedRegions != null && !providedRegions.isEmpty()) {
                // Use provided regions (for comparison)
                log.info("[UNIFIED] Using {} provided regions", providedRegions.size());
                regions = convertOverlaysToRegions(providedRegions);
            } else {
                // Auto-detect with YOLO
                log.info("[UNIFIED] Auto-detecting objects with YOLO");
                detectedShapes = segmentationService.detect(keyframeUrl);
                log.info("[UNIFIED] YOLO detected {} shapes", detectedShapes.size());
                
                if (!detectedShapes.isEmpty()) {
                    regions = convertShapesToRegions(detectedShapes);
                }
            }
            
            // Step 3: Qwen VL analysis
            if (regions.isEmpty()) {
                // Create dummy region for scene analysis only
                log.info("[UNIFIED] No regions, creating full-frame region for scene analysis");
                regions.add(new ObjectLabelService.RegionBox("full", 0.0, 0.0, 1.0, 1.0));
            }
            
            log.info("[UNIFIED] Calling Qwen VL with {} regions", regions.size());
            Map<String, ObjectLabelService.LabelResult> vlResults = 
                objectLabelService.labelRegions(keyframeUrl, regions, language != null ? language : "zh-CN");
            
            // Step 4: Extract VL data
            if (!vlResults.isEmpty()) {
                ObjectLabelService.LabelResult firstResult = vlResults.values().iterator().next();
                result.setVlRawResponse(firstResult.rawResponse);
                result.setVlSceneAnalysis(firstResult.sceneAnalysis);
                log.info("[UNIFIED] VL analysis complete - rawResponse: {}, sceneAnalysis: {}",
                    firstResult.rawResponse != null ? firstResult.rawResponse.length() + " chars" : "null",
                    firstResult.sceneAnalysis != null ? firstResult.sceneAnalysis.length() + " chars" : "null");
            }
            
            // Step 5: Build overlay objects/polygons
            if (!detectedShapes.isEmpty()) {
                buildOverlays(result, detectedShapes, vlResults);
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
        List<com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon> polygons = new ArrayList<>();
        
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
                // Create GoogleVisionProvider.OverlayPolygon
                com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon scenePolygon = 
                    new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon();
                scenePolygon.setLabel(polygon.label());
                scenePolygon.setLabelZh(labelZh);
                scenePolygon.setLabelLocalized(labelZh);
                scenePolygon.setConfidence(polygon.confidence());
                
                // Convert points
                List<com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point> scenePoints = new ArrayList<>();
                for (com.example.demo.ai.seg.dto.Point p : polygon.points()) {
                    scenePoints.add(new com.example.demo.ai.providers.vision.GoogleVisionProvider.OverlayPolygon.Point(
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
                obj.setConfidence(box.confidence());
                obj.setX(box.x());
                obj.setY(box.y());
                obj.setWidth(box.w());
                obj.setHeight(box.h());
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
