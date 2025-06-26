package com.example.demo.model;

import java.time.Duration;
import java.util.List;

public class SceneSegment {
    private Duration startTime;
    private Duration endTime;
    private List<String> labels;
    private boolean personPresent;

    public SceneSegment() {
    }

    public SceneSegment(Duration startTime, Duration endTime, List<String> labels, boolean personPresent) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.labels = labels;
        this.personPresent = personPresent;
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
}