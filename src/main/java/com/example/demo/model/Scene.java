package com.example.demo.model;

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
}