package com.example.demo.ai;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Video;
import org.springframework.stereotype.Service;

@Service
public class DefaultAITemplateGeneratorImpl implements DefaultAITemplateGenerator {

    @Override
    public ManualTemplate generateTemplate(Video video) {
        ManualTemplate template = new ManualTemplate();
        template.setUserId(video.getUserId());
        template.setVideoId(video.getId());

        template.setTemplateTitle(video.getTitle() + " Basic Template");
        template.setTotalVideoLength(0);  // Placeholder
        template.setTargetAudience("Not specified");
        template.setTone("Neutral");

        return template;
    }
}