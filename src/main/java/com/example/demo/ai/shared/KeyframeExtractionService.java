package com.example.demo.ai.shared;

import java.time.Duration;

public interface KeyframeExtractionService {
    String extractKeyframe(String videoUrl, Duration startTime, Duration endTime);
}