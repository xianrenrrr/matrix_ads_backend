package com.example.demo.ai.config;

import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.yolo.YoloV8SegService;
import com.example.demo.ai.seg.paddledet.PaddleDetSegService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiProviderConfig {
    
    @Autowired(required = false)
    private YoloV8SegService yolo;
    
    @Autowired(required = false)
    private PaddleDetSegService paddle;
    
    @Bean
    @Primary
    public SegmentationService segmentationService(
            @Value("${ai.seg.provider:yolov8}") String provider) {
        
        if ("paddledet".equalsIgnoreCase(provider) && paddle != null) {
            return paddle;
        }
        if (yolo != null) {
            return yolo;
        }
        // Fallback - create a default instance
        return paddle != null ? paddle : new PaddleDetSegService();
    }
}