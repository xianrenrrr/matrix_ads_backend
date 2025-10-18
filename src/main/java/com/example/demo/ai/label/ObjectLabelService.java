package com.example.demo.ai.label;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ObjectLabelService {
    String labelZh(byte[] imageBytes);

    // Minimal batch API for region-by-id labeling (backward compatible default)
    default Map<String, LabelResult> labelRegions(String keyframeUrl, List<RegionBox> regions, String locale) {
        return Collections.emptyMap();
    }
    
    // NEW: Video-aware region labeling (uses full video for temporal context)
    default Map<String, LabelResult> labelRegionsWithVideo(String videoUrl, String keyframeUrl, List<RegionBox> regions, String locale) {
        // Fallback to image-only analysis if not implemented
        return labelRegions(keyframeUrl, regions, locale);
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
    
    /**
     * NEW: Unified video analysis - analyzes entire scene video in one call.
     * Returns complete scene understanding: objects, labels, motion, description, audio.
     * This replaces the multi-step process of segmentation + labeling.
     * 
     * @param videoUrl Full scene video URL
     * @param locale Language for labels (e.g., "zh-CN", "en")
     * @return VideoAnalysisResult with complete scene analysis
     */
    default VideoAnalysisResult analyzeSceneVideo(String videoUrl, String locale) {
        return analyzeSceneVideo(videoUrl, locale, null);
    }
    
    /**
     * Unified video analysis with optional user context
     * 
     * @param videoUrl Full scene video URL
     * @param locale Language for labels (e.g., "zh-CN", "en")
     * @param userDescription Optional user-provided description for context
     * @return VideoAnalysisResult with complete scene analysis
     */
    default VideoAnalysisResult analyzeSceneVideo(String videoUrl, String locale, String userDescription) {
        return null; // Default no-op for backward compatibility
    }
    
    /**
     * Result of unified video analysis
     */
    public static class VideoAnalysisResult {
        public List<DetectedObject> objects;
        public String sceneDescription;
        public String dominantAction;
        public String audioContext;
        public String rawVLResponse;  // Complete VL JSON for caching
        
        public VideoAnalysisResult() {}
        
        public static class DetectedObject {
            public String id;
            public String labelZh;
            public String labelEn;
            public double confidence;
            public BoundingBox boundingBox;
            public String motionDescription;  // NEW: How object moves
            
            public DetectedObject() {}
            
            public static class BoundingBox {
                public double x, y, w, h;  // Normalized [0,1]
                public BoundingBox() {}
                public BoundingBox(double x, double y, double w, double h) {
                    this.x = x; this.y = y; this.w = w; this.h = h;
                }
            }
        }
    }
}
