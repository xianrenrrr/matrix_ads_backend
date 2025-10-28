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
        public LabelResult() {}
        public LabelResult(String id, String labelZh, double conf) {
            this.id = id; this.labelZh = labelZh; this.conf = conf;
        }
    }

    /**
     * Generate Chinese template metadata and per-scene guidance in one call.
     * Default no-op for backward compatibility (returns null when unimplemented).
     */
    default Map<String, Object> generateTemplateGuidance(Map<String, Object> payload) {
        return null;
    }
}
