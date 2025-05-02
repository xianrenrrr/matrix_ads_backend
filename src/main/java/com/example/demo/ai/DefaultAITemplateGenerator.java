package com.example.demo.ai;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;
import org.springframework.stereotype.Component;

@Component
public class DefaultAITemplateGenerator implements AITemplateGenerator {
    @Override
    public ManualTemplate generateTemplate(Video video) {
        ManualTemplate template = new ManualTemplate();
        template.setUserId(video.getUserId());
        template.setVideoId(video.getId());
        
        // Default template generation logic
        template.setTemplateTitle(video.getTitle() + " AI Template");
        
        // Placeholder for future AI-driven template generation logic
        template.setTotalVideoLength(0);  // To be determined
        template.setTargetAudience("Not specified");
        template.setTone("Neutral");
        
        return template;
    }
}
