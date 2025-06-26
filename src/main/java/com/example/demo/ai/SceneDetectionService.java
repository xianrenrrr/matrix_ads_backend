package com.example.demo.ai;

import com.example.demo.model.SceneSegment;
import java.util.List;

public interface SceneDetectionService {
    List<SceneSegment> detectScenes(String videoUrl);
}