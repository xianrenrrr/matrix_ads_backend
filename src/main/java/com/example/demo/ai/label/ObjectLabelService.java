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
}
