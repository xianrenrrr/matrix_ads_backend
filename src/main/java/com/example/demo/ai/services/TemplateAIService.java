package com.example.demo.ai.services;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;

public interface TemplateAIService {
    ManualTemplate generateTemplate(Video video);

    ManualTemplate generateTemplate(Video video, String language, String userDescription, Double sceneThresholdOverride);

    default ManualTemplate generateTemplate(Video video, String language) {
        return generateTemplate(video, language, null, null);
    }

    default ManualTemplate generateTemplate(Video video, String language, String userDescription) {
        return generateTemplate(video, language, userDescription, null);
    }
}
