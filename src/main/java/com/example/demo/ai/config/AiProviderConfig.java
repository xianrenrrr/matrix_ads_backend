package com.example.demo.ai.config;

import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.yolo.YoloV8SegService;
import com.example.demo.ai.seg.paddledet.PaddleDetSegService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {
    
    @Bean
    public SegmentationService segmentationService(
            @Value("${ai.seg.provider:yolov8}") String provider,
            YoloV8SegService yolo,
            PaddleDetSegService paddle) {
        
        if ("paddledet".equalsIgnoreCase(provider)) {
            return paddle;
        }
        return yolo;
    }
}