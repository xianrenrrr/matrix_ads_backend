package com.example.demo.ai.template;

import com.example.demo.model.SceneSegment;
import java.util.List;

public interface SceneDetectionService {
    List<SceneSegment> detectScenes(String videoUrl);
}