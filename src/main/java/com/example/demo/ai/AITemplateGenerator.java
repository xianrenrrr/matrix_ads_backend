package com.example.demo.ai;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;

public interface AITemplateGenerator {
    ManualTemplate generateTemplate(Video video);
}