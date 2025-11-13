package com.example.demo.model;

import java.time.Duration;
import java.util.List;

public class SceneSegment {
    private Long startTimeMs;
    private Long endTimeMs;
    private List<String> labels;
    private boolean personPresent;
    private List<Scene.KeyElement> keyElements;  // Key elements with optional bounding boxes

    public SceneSegment() {
    }

    public SceneSegment(Duration startTime, Duration endTime, List<String> labels, boolean personPresent) {
        this.startTimeMs = startTime.toMillis();
        this.endTimeMs = endTime.toMillis();
        this.labels = labels;
        this.personPresent = personPresent;
    }

    public Duration getStartTime() {
        return startTimeMs != null ? Duration.ofMillis(startTimeMs) : null;
    }

    public void setStartTime(Duration startTime) {
        this.startTimeMs = startTime != null ? startTime.toMillis() : null;
    }

    public Duration getEndTime() {
        return endTimeMs != null ? Duration.ofMillis(endTimeMs) : null;
    }

    public void setEndTime(Duration endTime) {
        this.endTimeMs = endTime != null ? endTime.toMillis() : null;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(Long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    public void setEndTimeMs(Long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public boolean isPersonPresent() {
        return personPresent;
    }

    public void setPersonPresent(boolean personPresent) {
        this.personPresent = personPresent;
    }
    
    public List<Scene.KeyElement> getKeyElements() {
        return keyElements;
    }
    
    public void setKeyElements(List<Scene.KeyElement> keyElements) {
        this.keyElements = keyElements;
    }
}
// Change Log: Replaced overlayObjects with keyElements (unified system with optional bounding boxes)