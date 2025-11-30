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
        return analyzeScene(videoUrl, language, startTime, endTime, null, null, null);
    }
    
    /**
     * Analyze a scene video with subtitle context (simplified - no providedRegions)
     * 
     * @param videoUrl Video URL to analyze
     * @param language Language for analysis (zh-CN, en, etc.)
     * @param startTime Optional start time for keyframe extraction
     * @param endTime Optional end time for keyframe extraction
     * @param subtitleText Optional subtitle text for this scene (enhances VL analysis)
     * @return SceneAnalysisResult with VL data
     */
    public SceneAnalysisResult analyzeScene(
        String videoUrl,
        String language,
        Duration startTime,
        Duration endTime,
        String subtitleText
    ) {
        return analyzeScene(videoUrl, language, startTime, endTime, subtitleText, null, null);
    }
    
    /**
     * Analyze a scene video with subtitle context and Azure object hints
     */
    public SceneAnalysisResult analyzeScene(
        String videoUrl,
        String language,
        Duration startTime,
        Duration endTime,
        String subtitleText,
        List<String> azureObjectHints
    ) {
        return analyzeScene(videoUrl, language, startTime, endTime, subtitleText, azureObjectHints, null);
    }
    
    /**
     * Analyze a scene video with full context including combined scriptLines from all scenes
     * 
     * @param videoUrl Video URL to analyze
     * @param language Language for analysis (zh-CN, en, etc.)
     * @param startTime Optional start time for keyframe extraction
     * @param endTime Optional end time for keyframe extraction
     * @param subtitleText Optional subtitle text for this scene (enhances VL analysis)
     * @param azureObjectHints Optional list of object names detected by Azure (for targeted grounding)
     * @param combinedScriptLines Optional combined scriptLines from all scenes (for full template context)
     * @return SceneAnalysisResult with VL data
     */
    public SceneAnalysisResult analyzeScene(
        String videoUrl,
        String language,
        Duration startTime,
        Duration endTime,
        String subtitleText,
        List<String> azureObjectHints,
        String combinedScriptLines
    ) {
        log.info("[UNIFIED] Analyzing scene: videoUrl={}, language={}, hasSubtitles={}, azureHints={}", 
            videoUrl != null ? videoUrl.substring(0, Math.min(50, videoUrl.length())) + "..." : "null",
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
                // Pass Azure object hints for targeted grounding and combined scriptLines for full context
                vlResults = objectLabelService.labelRegions(
                    keyframeUrl, 
                    dummyRegion,
                    language != null ? language : "zh-CN",
                    subtitleText,  // Pass subtitle context to VL
                    azureObjectHints,  // Pass Azure detected objects as hints
                    combinedScriptLines  // Pass combined scriptLines from all scenes
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
                    if (firstResult.keyElementsWithBoxes != null && !firstResult.keyElementsWithBoxes.isEmpty()) {
                        result.setKeyElementsWithBoxes(firstResult.keyElementsWithBoxes);
                        log.info("[UNIFIED] Extracted {} key elements from VL: {}", 
                            firstResult.keyElementsWithBoxes.size(), firstResult.keyElementsWithBoxes);
                    } else {
                        log.warn("[UNIFIED] No key elements in VL result");
                    }
                    
                    log.info("[UNIFIED] VL analysis complete - rawResponse: {}, sceneAnalysis: {}, keyElements: {}",
                        firstResult.rawResponse != null ? firstResult.rawResponse.length() + " chars" : "null",
                        firstResult.sceneAnalysis != null ? firstResult.sceneAnalysis.length() + " chars" : "null",
                        firstResult.keyElementsWithBoxes != null ? firstResult.keyElementsWithBoxes.size() : 0);
                } else {
                    log.warn("[UNIFIED] VL returned empty results");
                }
            } catch (Exception vlEx) {
                log.error("[UNIFIED] VL analysis failed: {} - {}", vlEx.getClass().getSimpleName(), vlEx.getMessage());
                // Continue without VL data - will default to grid overlay below
            }
            
            // Step 3: Extract keyElements from Qwen VL results
            if (!vlResults.isEmpty()) {
                ObjectLabelService.LabelResult firstResult = vlResults.values().iterator().next();
                
                // Use keyElementsWithBoxes from VL result (unified format)
                if (firstResult.keyElementsWithBoxes != null && !firstResult.keyElementsWithBoxes.isEmpty()) {
                    // Deduplicate keyElements by name (case-insensitive), keeping highest confidence
                    List<Scene.KeyElement> deduplicated = deduplicateKeyElements(firstResult.keyElementsWithBoxes);
                    result.setKeyElementsWithBoxes(deduplicated);
                    
                    // Set shortLabelZh from first keyElement
                    result.setShortLabelZh(deduplicated.get(0).getName());
                    
                    log.info("[UNIFIED] ✅ Extracted {} keyElements from Qwen VL (after deduplication: {})", 
                        firstResult.keyElementsWithBoxes.size(), deduplicated.size());
                    for (Scene.KeyElement ke : deduplicated) {
                        if (ke.getBox() != null && ke.getBox().size() >= 4) {
                            log.info("[UNIFIED]    - {} at [{},{},{},{}] conf:{}", 
                                ke.getName(), ke.getBox().get(0), ke.getBox().get(1), ke.getBox().get(2), ke.getBox().get(3), ke.getConfidence());
                        } else {
                            log.info("[UNIFIED]    - {} (abstract concept, no box) conf:{}", 
                                ke.getName(), ke.getConfidence());
                        }
                    }
                } else {
                    log.warn("[UNIFIED] ⚠️  No keyElements returned by Qwen VL");
                }
            }
            
            log.info("[UNIFIED] Analysis complete - keyElements: {}", 
                result.getKeyElementsWithBoxes() != null ? result.getKeyElementsWithBoxes().size() : 0);
            
        } catch (Exception e) {
            log.error("[UNIFIED] Analysis failed: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Deduplicate keyElements by name (case-insensitive), keeping the one with highest confidence
     */
    private List<Scene.KeyElement> deduplicateKeyElements(List<Scene.KeyElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return elements;
        }
        
        // Use a map to track best element for each name (case-insensitive)
        Map<String, Scene.KeyElement> bestByName = new java.util.LinkedHashMap<>();
        
        for (Scene.KeyElement element : elements) {
            String nameLower = element.getName().toLowerCase();
            
            // If we haven't seen this name, or this one has higher confidence, keep it
            if (!bestByName.containsKey(nameLower) || 
                element.getConfidence() > bestByName.get(nameLower).getConfidence()) {
                bestByName.put(nameLower, element);
            } else {
                log.info("[UNIFIED-DEDUP] Removing duplicate: {} (conf: {}) - keeping existing with conf: {}", 
                    element.getName(), element.getConfidence(), bestByName.get(nameLower).getConfidence());
            }
        }
        
        return new ArrayList<>(bestByName.values());
    }

}
