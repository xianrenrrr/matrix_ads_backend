package com.example.demo.ai.services;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;

public interface TemplateAIService {
    ManualTemplate generateTemplate(Video video);

    ManualTemplate generateTemplate(Video video, String language, String userDescription);

    default ManualTemplate generateTemplate(Video video, String language) {
        return generateTemplate(video, language, null);
    }
}
