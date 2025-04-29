package com.example.demo.model;

import java.util.List;
import java.util.ArrayList;

public class ManualTemplate {
    private String userId;
    private String templateTitle;
    private int totalVideoLength;
    private String targetAudience;
    private String tone;
    private List<Scene> scenes;
    private String videoFormat;
    private String lightingRequirements;
    private String soundRequirements;
    private String id;

    public ManualTemplate() {
        this.scenes = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getId() {
        return id;
    }
    
    // Setter
    public void setId(String id) {
        this.id = id;
    }

    // Getters
    public String getTemplateTitle() {
        return templateTitle;
    }

    public int getTotalVideoLength() {
        return totalVideoLength;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public String getTone() {
        return tone;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    public String getVideoFormat() {
        return videoFormat;
    }

    public String getLightingRequirements() {
        return lightingRequirements;
    }

    public String getSoundRequirements() {
        return soundRequirements;
    }

    // Setters
    public void setTemplateTitle(String templateTitle) {
        this.templateTitle = templateTitle;
    }

    public void setTotalVideoLength(int totalVideoLength) {
        this.totalVideoLength = totalVideoLength;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public void setScenes(List<Scene> scenes) {
        this.scenes = scenes;
    }

    public void setVideoFormat(String videoFormat) {
        this.videoFormat = videoFormat;
    }

    public void setLightingRequirements(String lightingRequirements) {
        this.lightingRequirements = lightingRequirements;
    }

    public void setSoundRequirements(String soundRequirements) {
        this.soundRequirements = soundRequirements;
    }

    public static class Scene {
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
            this.screenGridOverlay = new ArrayList<>();
            this.screenGridOverlayLabels = new ArrayList<>();
        }
        //getters
        public int getSceneNumber() {
            return sceneNumber;
        }

        public String getSceneTitle() {
            return sceneTitle;
        }

        public int getSceneDuration() {
            return sceneDuration;
        }

        public String getScriptLine() {
            return scriptLine;
        }

        public boolean isPresenceOfPerson() {
            return presenceOfPerson;
        }

        public String getPreferredGender() {
            return preferredGender;
        }

        public String getPersonPosition() {
            return personPosition;
        }

        public String getDeviceOrientation() {
            return deviceOrientation;
        }

        public List<Integer> getScreenGridOverlay() {
            return screenGridOverlay;
        }

        public List<String> getScreenGridOverlayLabels() {
            return screenGridOverlayLabels;
        }

        public String getBackgroundInstructions() {
            return backgroundInstructions;
        }

        public String getSpecificCameraInstructions() {
            return specificCameraInstructions;
        }

        public String getMovementInstructions() {
            return movementInstructions;
        }

        public String getAudioNotes() {
            return audioNotes;
        }

        public String getExampleFrame() {
            return exampleFrame;
        }

        public String getOtherNotes() {
            return otherNotes;
        }

        // Setters
        public void setSceneNumber(int sceneNumber) {
            this.sceneNumber = sceneNumber;
        }

        public void setSceneTitle(String sceneTitle) {
            this.sceneTitle = sceneTitle;
        }

        public void setSceneDuration(int sceneDuration) {
            this.sceneDuration = sceneDuration;
        }

        public void setScriptLine(String scriptLine) {
            this.scriptLine = scriptLine;
        }

        public void setPresenceOfPerson(boolean presenceOfPerson) {
            this.presenceOfPerson = presenceOfPerson;
        }

        public void setPreferredGender(String preferredGender) {
            this.preferredGender = preferredGender;
        }

        public void setPersonPosition(String personPosition) {
            this.personPosition = personPosition;
        }

        public void setDeviceOrientation(String deviceOrientation) {
            this.deviceOrientation = deviceOrientation;
        }

        public void setScreenGridOverlay(List<Integer> screenGridOverlay) {
            this.screenGridOverlay = screenGridOverlay;
        }

        public void setScreenGridOverlayLabels(List<String> screenGridOverlayLabels) {
            this.screenGridOverlayLabels = screenGridOverlayLabels;
        }

        public void setBackgroundInstructions(String backgroundInstructions) {
            this.backgroundInstructions = backgroundInstructions;
        }

        public void setSpecificCameraInstructions(String specificCameraInstructions) {
            this.specificCameraInstructions = specificCameraInstructions;
        }

        public void setMovementInstructions(String movementInstructions) {
            this.movementInstructions = movementInstructions;
        }

        public void setAudioNotes(String audioNotes) {
            this.audioNotes = audioNotes;
        }

        public void setExampleFrame(String exampleFrame) {
            this.exampleFrame = exampleFrame;
        }

        public void setOtherNotes(String otherNotes) {
            this.otherNotes = otherNotes;
        }
    }
}