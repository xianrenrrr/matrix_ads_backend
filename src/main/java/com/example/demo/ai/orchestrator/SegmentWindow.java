package com.example.demo.ai.orchestrator;

import java.time.Duration;

/**
 * Represents a time window for targeted video analysis refinement.
 * Each window corresponds to a scene that needs better object detection
 * with padding around the original scene boundaries.
 */
public class SegmentWindow {
    private Duration startTime;
    private Duration endTime;
    private int originalSceneIndex;
    private String reason;
    
    public SegmentWindow() {}
    
    public SegmentWindow(Duration startTime, Duration endTime, int originalSceneIndex, String reason) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.originalSceneIndex = originalSceneIndex;
        this.reason = reason;
    }
    
    public Duration getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Duration startTime) {
        this.startTime = startTime;
    }
    
    public Duration getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Duration endTime) {
        this.endTime = endTime;
    }
    
    public int getOriginalSceneIndex() {
        return originalSceneIndex;
    }
    
    public void setOriginalSceneIndex(int originalSceneIndex) {
        this.originalSceneIndex = originalSceneIndex;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public long getDurationSeconds() {
        if (startTime != null && endTime != null) {
            return endTime.minus(startTime).getSeconds();
        }
        return 0;
    }
    
    @Override
    public String toString() {
        return String.format("SegmentWindow{scene=%d, %s-%s (%ds), reason='%s'}", 
                           originalSceneIndex, startTime, endTime, getDurationSeconds(), reason);
    }
}