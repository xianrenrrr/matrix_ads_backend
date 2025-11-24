package com.example.demo.model;

import com.example.demo.ai.subtitle.SubtitleSegment;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Scene {
    private int sceneNumber;
    private String sceneTitle;
    private String sceneDescription;  // NEW: Scene description for manual templates
    @JsonDeserialize(using = DurationToLongDeserializer.class)
    private long sceneDurationInSeconds;
    private String scriptLine;
    private boolean presenceOfPerson;
    private String deviceOrientation;
    private String backgroundInstructions;
    private String specificCameraInstructions;
    private String movementInstructions;
    private String audioNotes;
    
    // New AI-generated fields
    private Long startTimeMs;
    private Long endTimeMs;
    private String keyframeUrl;
    private String videoId;  // NEW: For manual templates - ID of the scene's video
    
    // Scene metadata
    private String sceneSource;  // "manual" | "ai" - how the scene was created
    private String sourceAspect;  // e.g., "9:16" for mini-app pixel mapping
    private String shortLabelZh;  // Chinese short label for the dominant object
    
    // VL Analysis Output (for comparison and reasoning)
    private String vlRawResponse;  // Complete raw VL API response (JSON string)
    private String vlSceneAnalysis;  // Detailed scene analysis from VL (for video comparison)
    
    // Key elements with optional bounding boxes (unified system)
    private List<KeyElement> keyElementsWithBoxes;
    
    // Subtitle segments for KTV-style display and video compilation
    private List<SubtitleSegment> subtitleSegments;
    
    // Inner class for key element with optional bounding box
    // This unified structure replaces the old ObjectOverlay system
    public static class KeyElement {
        private String name;  // e.g., "车载大屏导航", "销售人员"
        private List<Float> box;  // Optional bounding box [x, y, width, height] in 0-1 range, null if no box
        private float confidence;  // Confidence score 0-1
        
        public KeyElement() {}
        
        public KeyElement(String name, List<Float> box, float confidence) {
            this.name = name;
            this.box = box;
            this.confidence = confidence;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public List<Float> getBox() { return box; }
        public void setBox(List<Float> box) { this.box = box; }
        
        public float getConfidence() { return confidence; }
        public void setConfidence(float confidence) { this.confidence = confidence; }
    }

    public Scene() {
    }

    public int getSceneNumber() {
        return sceneNumber;
    }

    public void setSceneNumber(int sceneNumber) {
        this.sceneNumber = sceneNumber;
    }

    public String getSceneTitle() {
        return sceneTitle;
    }

    public void setSceneTitle(String sceneTitle) {
        this.sceneTitle = sceneTitle;
    }
    
    public String getSceneDescription() {
        return sceneDescription;
    }
    
    public void setSceneDescription(String sceneDescription) {
        this.sceneDescription = sceneDescription;
    }

    public long getSceneDurationInSeconds() {
        return sceneDurationInSeconds;
    }

    public void setSceneDurationInSeconds(long sceneDurationInSeconds) {
        this.sceneDurationInSeconds = sceneDurationInSeconds;
    }

    // Helper methods for Duration compatibility
    public Duration getSceneDuration() {
        return Duration.ofSeconds(sceneDurationInSeconds);
    }

    public void setSceneDuration(Duration duration) {
        this.sceneDurationInSeconds = duration != null ? duration.getSeconds() : 0;
    }

    public void setSceneDuration(int durationSeconds) {
        this.sceneDurationInSeconds = durationSeconds;
    }

    public String getScriptLine() {
        return scriptLine;
    }

    public void setScriptLine(String scriptLine) {
        this.scriptLine = scriptLine;
    }

    public boolean isPresenceOfPerson() {
        return presenceOfPerson;
    }

    public void setPresenceOfPerson(boolean presenceOfPerson) {
        this.presenceOfPerson = presenceOfPerson;
    }

    public String getDeviceOrientation() {
        return deviceOrientation;
    }

    public void setDeviceOrientation(String deviceOrientation) {
        this.deviceOrientation = deviceOrientation;
    }

    public String getBackgroundInstructions() {
        return backgroundInstructions;
    }

    public void setBackgroundInstructions(String backgroundInstructions) {
        this.backgroundInstructions = backgroundInstructions;
    }

    public String getSpecificCameraInstructions() {
        return specificCameraInstructions;
    }

    public void setSpecificCameraInstructions(String specificCameraInstructions) {
        this.specificCameraInstructions = specificCameraInstructions;
    }

    public String getMovementInstructions() {
        return movementInstructions;
    }

    public void setMovementInstructions(String movementInstructions) {
        this.movementInstructions = movementInstructions;
    }

    public String getAudioNotes() {
        return audioNotes;
    }

    public void setAudioNotes(String audioNotes) {
        this.audioNotes = audioNotes;
    }
    
    // Getters and setters for new AI fields
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

    public String getKeyframeUrl() {
        return keyframeUrl;
    }

    public void setKeyframeUrl(String keyframeUrl) {
        this.keyframeUrl = keyframeUrl;
    }
    
    public String getVideoId() {
        return videoId;
    }
    
    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }
    
    // Getters and setters for dual scene system fields
    public String getSceneSource() {
        return sceneSource;
    }
    
    public void setSceneSource(String sceneSource) {
        this.sceneSource = sceneSource;
    }
    

    
    public String getSourceAspect() {
        return sourceAspect;
    }
    
    public void setSourceAspect(String sourceAspect) {
        this.sourceAspect = sourceAspect;
    }
    
    public String getShortLabelZh() {
        return shortLabelZh;
    }
    
    public void setShortLabelZh(String shortLabelZh) {
        this.shortLabelZh = shortLabelZh;
    }
    
    public String getVlRawResponse() {
        return vlRawResponse;
    }
    
    public void setVlRawResponse(String vlRawResponse) {
        this.vlRawResponse = vlRawResponse;
    }
    
    public String getVlSceneAnalysis() {
        return vlSceneAnalysis;
    }
    
    public void setVlSceneAnalysis(String vlSceneAnalysis) {
        this.vlSceneAnalysis = vlSceneAnalysis;
    }
    

    
    public List<KeyElement> getKeyElementsWithBoxes() {
        return keyElementsWithBoxes;
    }
    
    public void setKeyElementsWithBoxes(List<KeyElement> keyElementsWithBoxes) {
        this.keyElementsWithBoxes = keyElementsWithBoxes;
    }
    
    public List<SubtitleSegment> getSubtitleSegments() {
        return subtitleSegments;
    }
    
    public void setSubtitleSegments(List<SubtitleSegment> subtitleSegments) {
        this.subtitleSegments = subtitleSegments;
    }
    
}
// Change Log: Unified keyElements system - removed ObjectOverlay in favor of KeyElement with optional box