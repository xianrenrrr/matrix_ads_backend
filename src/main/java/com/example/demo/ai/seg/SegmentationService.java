package com.example.demo.ai.seg;

import com.example.demo.ai.seg.dto.OverlayShape;
import java.util.List;

public interface SegmentationService {
    List<OverlayShape> detect(String keyframeUrl);
}