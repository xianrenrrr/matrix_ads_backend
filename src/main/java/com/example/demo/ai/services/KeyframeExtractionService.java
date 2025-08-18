package com.example.demo.ai.services;

import java.time.Duration;

public interface KeyframeExtractionService {
    String extractKeyframe(String videoUrl, Duration startTime, Duration endTime);
}