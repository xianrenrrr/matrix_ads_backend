package com.example.demo.ai.template;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;

public interface DefaultAITemplateGenerator {
    ManualTemplate generateTemplate(Video video);
}