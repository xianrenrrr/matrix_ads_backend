package com.example.demo.model;

import com.example.demo.ai.vision.ObjectLocalizationService.OverlayPolygon;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class Scene {
    private int sceneNumber;
    private String sceneTitle;
    private long sceneDurationInSeconds;
    private String scriptLine;
    private boolean presenceOfPerson;
    private String preferredGender;
    private String personPosition;
    private String deviceOrientation;
    private List<Integer> screenGridOverlay;
    private List<String> screenGridOverlayLabels;
    private String backgroundInstructions;
    private String specificCameraInstructions;
    private String movementInstructions;
    private String audioNotes;
    private String exampleFrame;
    private String otherNotes;
    
    // New AI-generated fields
    private Long startTimeMs;
    private Long endTimeMs;
    private String keyframeUrl;
    private Map<String, String> blockImageUrls;
    private Map<String, String> blockDescriptions;
    
    // Dual scene system fields (add-only, non-breaking)
    private String sceneSource;  // "manual" | "ai" - how the scene was created
    private String overlayType;  // "grid" | "objects" | "polygons" - how to render guidance
    private List<ObjectOverlay> overlayObjects;  // only when overlayType="objects"
    private List<OverlayPolygon> overlayPolygons; // only when overlayType="polygons"
    
    // Inner class for object overlay data
    public static class ObjectOverlay {
        private String label;
        private String labelLocalized;  // Localized label (e.g., Chinese translation)
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
        
        // Keep backward compatibility getters for now
        @Deprecated
        public float getW() { return width; }
        @Deprecated
        public void setW(float w) { this.width = w; }
        
        @Deprecated
        public float getH() { return height; }
        @Deprecated
        public void setH(float h) { this.height = h; }
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

    public String getPreferredGender() {
        return preferredGender;
    }

    public void setPreferredGender(String preferredGender) {
        this.preferredGender = preferredGender;
    }

    public String getPersonPosition() {
        return personPosition;
    }

    public void setPersonPosition(String personPosition) {
        this.personPosition = personPosition;
    }

    public String getDeviceOrientation() {
        return deviceOrientation;
    }

    public void setDeviceOrientation(String deviceOrientation) {
        this.deviceOrientation = deviceOrientation;
    }

    public List<Integer> getScreenGridOverlay() {
        return screenGridOverlay;
    }

    public void setScreenGridOverlay(List<Integer> screenGridOverlay) {
        this.screenGridOverlay = screenGridOverlay;
    }

    public List<String> getScreenGridOverlayLabels() {
        return screenGridOverlayLabels;
    }

    public void setScreenGridOverlayLabels(List<String> screenGridOverlayLabels) {
        this.screenGridOverlayLabels = screenGridOverlayLabels;
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

    public String getExampleFrame() {
        return exampleFrame;
    }

    public void setExampleFrame(String exampleFrame) {
        this.exampleFrame = exampleFrame;
    }

    public String getOtherNotes() {
        return otherNotes;
    }

    public void setOtherNotes(String otherNotes) {
        this.otherNotes = otherNotes;
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

    public Map<String, String> getBlockImageUrls() {
        return blockImageUrls;
    }

    public void setBlockImageUrls(Map<String, String> blockImageUrls) {
        this.blockImageUrls = blockImageUrls;
    }

    public Map<String, String> getBlockDescriptions() {
        return blockDescriptions;
    }

    public void setBlockDescriptions(Map<String, String> blockDescriptions) {
        this.blockDescriptions = blockDescriptions;
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
    
    public List<OverlayPolygon> getOverlayPolygons() {
        return overlayPolygons;
    }
    
    public void setOverlayPolygons(List<OverlayPolygon> overlayPolygons) {
        this.overlayPolygons = overlayPolygons;
    }
}
// Change Log: Added dual scene system fields (sceneSource, overlayType, overlayObjects) for object overlay support