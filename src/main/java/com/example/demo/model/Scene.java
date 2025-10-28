package com.example.demo.model;

import com.example.demo.ai.seg.dto.OverlayPolygonClass;
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
    
    // Dual scene system fields (add-only, non-breaking)
    private String sceneSource;  // "manual" | "ai" - how the scene was created
    private String overlayType;  // "grid" | "objects" | "polygons" - how to render guidance
    private List<ObjectOverlay> overlayObjects;  // only when overlayType="objects"
    private List<OverlayPolygonClass> overlayPolygons; // only when overlayType="polygons"
    private String sourceAspect;  // e.g., "9:16" for mini-app pixel mapping
    private String shortLabelZh;  // Chinese short label for the dominant object
    
    // VL Analysis Output (for comparison and reasoning)
    private String vlRawResponse;  // Complete raw VL API response (JSON string)
    private String vlSceneAnalysis;  // Detailed scene analysis from VL (for video comparison)
    
    // Subtitles for this scene (word-level timestamps)
    private List<SubtitleSegment> subtitles;
    
    // Key elements for this scene (for purpose-driven comparison)
    private List<String> keyElements;
    
    // Inner class for object overlay data
    public static class ObjectOverlay {
        private String label;
        private String labelLocalized;  // Localized label (e.g., Chinese translation)
        private String labelZh;  // Chinese label
        private float confidence;
        private float x;  // normalized [0,1], top-left corner
        private float y;  // normalized [0,1], top-left corner
        private float width;  // normalized [0,1], width
        private float height;  // normalized [0,1], height
        
        public ObjectOverlay() {}
        
        public ObjectOverlay(String label, float confidence, float x, float y, float width, float height) {
            this.label = label;
            this.confidence = confidence;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        
        public String getLabelLocalized() { return labelLocalized; }
        public void setLabelLocalized(String labelLocalized) { this.labelLocalized = labelLocalized; }
        
        public String getLabelZh() { return labelZh; }
        public void setLabelZh(String labelZh) { this.labelZh = labelZh; }
        
        public float getConfidence() { return confidence; }
        public void setConfidence(float confidence) { this.confidence = confidence; }
        
        public float getX() { return x; }
        public void setX(float x) { this.x = x; }
        
        public float getY() { return y; }
        public void setY(float y) { this.y = y; }
        
        public float getWidth() { return width; }
        public void setWidth(float width) { this.width = width; }
        
        public float getHeight() { return height; }
        public void setHeight(float height) { this.height = height; }

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

    // Helper methods for Duration compatibility - excluded from Firestore
    @com.google.cloud.firestore.annotation.Exclude
    public Duration getSceneDuration() {
        return Duration.ofSeconds(sceneDurationInSeconds);
    }

    @com.google.cloud.firestore.annotation.Exclude
    public void setSceneDuration(Duration duration) {
        this.sceneDurationInSeconds = duration != null ? duration.getSeconds() : 0;
    }

    @com.google.cloud.firestore.annotation.Exclude
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
    
    // Getters and setters for new AI fields - excluded from Firestore
    @com.google.cloud.firestore.annotation.Exclude
    public Duration getStartTime() {
        return startTimeMs != null ? Duration.ofMillis(startTimeMs) : null;
    }

    @com.google.cloud.firestore.annotation.Exclude
    public void setStartTime(Duration startTime) {
        this.startTimeMs = startTime != null ? startTime.toMillis() : null;
    }

    @com.google.cloud.firestore.annotation.Exclude
    public Duration getEndTime() {
        return endTimeMs != null ? Duration.ofMillis(endTimeMs) : null;
    }

    @com.google.cloud.firestore.annotation.Exclude
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
    
    public String getOverlayType() {
        return overlayType;
    }
    
    public void setOverlayType(String overlayType) {
        this.overlayType = overlayType;
    }
    
    public List<ObjectOverlay> getOverlayObjects() {
        return overlayObjects;
    }
    
    public void setOverlayObjects(List<ObjectOverlay> overlayObjects) {
        this.overlayObjects = overlayObjects;
    }
    
    public String getSourceAspect() {
        return sourceAspect;
    }
    
    public void setSourceAspect(String sourceAspect) {
        this.sourceAspect = sourceAspect;
    }
    
    public List<OverlayPolygonClass> getOverlayPolygons() {
        return overlayPolygons;
    }
    
    public void setOverlayPolygons(List<OverlayPolygonClass> overlayPolygons) {
        this.overlayPolygons = overlayPolygons;
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
    
    public List<SubtitleSegment> getSubtitles() {
        return subtitles;
    }
    
    public void setSubtitles(List<SubtitleSegment> subtitles) {
        this.subtitles = subtitles;
    }
    
    public List<String> getKeyElements() {
        return keyElements;
    }
    
    public void setKeyElements(List<String> keyElements) {
        this.keyElements = keyElements;
    }
    
}
// Change Log: Added dual scene system fields (sceneSource, overlayType, overlayObjects) for object overlay support