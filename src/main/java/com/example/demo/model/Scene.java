package com.example.demo.model;

import java.util.List;

public class Scene {
    private int sceneNumber;
    private String sceneTitle;
    private int sceneDuration;
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

    public int getSceneDuration() {
        return sceneDuration;
    }

    public void setSceneDuration(int sceneDuration) {
        this.sceneDuration = sceneDuration;
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
}