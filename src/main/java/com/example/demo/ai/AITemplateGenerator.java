package com.example.demo.ai;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;

public interface AITemplateGenerator {
    /**
     * Generate a template based on the video metadata
     * @param video The video for which to generate a template
     * @return A generated ManualTemplate
     */
    ManualTemplate generateTemplate(Video video);
}
