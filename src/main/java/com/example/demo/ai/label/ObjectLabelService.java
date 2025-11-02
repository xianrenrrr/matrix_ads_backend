package com.example.demo.ai.label;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service interface for AI-powered object labeling and scene analysis
 * 
 * Implementations:
 * - QwenVLPlusLabeler: Uses Alibaba Qwen VL model
 * 
 * Used by:
 * - UnifiedSceneAnalysisService: Scene analysis during template creation
 * - TemplateAIServiceImpl: Template metadata generation
 * - ContentManager: Manual template metadata generation
 */
public interface ObjectLabelService {
    
    /**
     * Legacy method - not currently used
     * @deprecated Use labelRegions() instead
     */
    @Deprecated
    default String labelZh(byte[] imageBytes) {
        return "未知";
    }
    
    // Minimal batch API for region-by-id labeling (backward compatible default)
    default Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale) {
        return labelRegions(keyframeUrl, regions, locale, null);
    }
    
    // Enhanced API with subtitle context for better scene understanding
    default Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale, String subtitleText) {
        return labelRegions(keyframeUrl, regions, locale, subtitleText, null);
    }
    
    // Enhanced API with Azure object hints for targeted grounding
    default Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale, String subtitleText, List<String> azureObjectHints) {
        return Collections.emptyMap();
    }

    // DTOs for region labeling (normalized [0,1])
    public static class RegionBox {
        public String id;
        public double x; // left
        public double y; // top
        public double w; // width
        public double h; // height
        public RegionBox() {}
        public RegionBox(String id, double x, double y, double w, double h) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    public static class LabelResult {
        public String id;
        public String labelZh;
        public double conf;
        public String sceneAnalysis;  // Detailed scene analysis from VL
        public String rawResponse;    // Raw VL response for debugging
        public List<String> keyElements;  // NEW - Key visual elements (3-5 items)
        public String scriptLine;  // NEW - Cleaned/validated ASR text
        public int[] box;  // NEW - Bounding box [x, y, width, height] in 0-1000 range
        public LabelResult() {}
        public LabelResult(String id, String labelZh, double conf) {
            this.id = id; this.labelZh = labelZh; this.conf = conf;
        }
    }

    /**
     * Generate Chinese template metadata and per-scene guidance (WITHOUT scriptLine cleaning).
     * Default no-op for backward compatibility (returns null when unimplemented).
     * 
     * @param payload Template and scene data
     */
    default Map<String, Object> generateTemplateGuidance(Map<String, Object> payload) {
        return null;
    }
    
    /**
     * Clean ASR transcript and assign to scenes based on timing.
     * For multi-scene videos (AI templates).
     * 
     * @param asrSegments All ASR segments with timing
     * @param scenes Scene timing and analysis data
     * @return Map with "scriptLines" array matching scene order
     */
    default Map<String, Object> cleanScriptLines(List<Map<String, Object>> asrSegments, List<Map<String, Object>> scenes) {
        return null;
    }
    
    /**
     * Clean ASR transcript for a single scene (simplified).
     * For single-scene videos (manual templates).
     * 
     * @param asrSegments ASR segments for this scene
     * @param videoDescription User's video description (context)
     * @param sceneDescription User's scene description (context)
     * @return Cleaned scriptLine text
     */
    default String cleanSingleScriptLine(List<Map<String, Object>> asrSegments, String videoDescription, String sceneDescription) {
        return null;
    }
}
