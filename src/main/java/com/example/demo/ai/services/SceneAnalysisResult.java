package com.example.demo.ai.services;

import com.example.demo.model.Scene;
import java.util.List;

/**
 * Result object from unified scene analysis
 * Contains all data needed to populate a Scene object
 */
public class SceneAnalysisResult {
    
    private String keyframeUrl;
    private String vlRawResponse;
    private String vlSceneAnalysis;
    private List<Scene.ObjectOverlay> overlayObjects;
    private String overlayType;  // "objects" or "grid"
    private String shortLabelZh;
    private String sourceAspect;  // e.g., "9:16" or "16:9"
    private List<String> keyElements;  // Key elements for this scene (for comparison)
    
    public SceneAnalysisResult() {}
    
    // Getters and Setters
    
    public String getKeyframeUrl() {
        return keyframeUrl;
    }
    
    public void setKeyframeUrl(String keyframeUrl) {
        this.keyframeUrl = keyframeUrl;
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
    
    public List<Scene.ObjectOverlay> getOverlayObjects() {
        return overlayObjects;
    }
    
    public void setOverlayObjects(List<Scene.ObjectOverlay> overlayObjects) {
        this.overlayObjects = overlayObjects;
    }
    
    public String getOverlayType() {
        return overlayType;
    }
    
    public void setOverlayType(String overlayType) {
        this.overlayType = overlayType;
    }
    
    public String getShortLabelZh() {
        return shortLabelZh;
    }
    
    public void setShortLabelZh(String shortLabelZh) {
        this.shortLabelZh = shortLabelZh;
    }

    
    public String getSourceAspect() {
        return sourceAspect;
    }
    
    public void setSourceAspect(String sourceAspect) {
        this.sourceAspect = sourceAspect;
    }
    
    /**
     * Apply this result to a Scene object
     */
    public void applyToScene(Scene scene) {
        if (keyframeUrl != null) {
            scene.setKeyframeUrl(keyframeUrl);
        }
        if (vlRawResponse != null) {
            scene.setVlRawResponse(vlRawResponse);
        }
        if (vlSceneAnalysis != null) {
            scene.setVlSceneAnalysis(vlSceneAnalysis);
        }
        if (overlayObjects != null) {
            scene.setOverlayObjects(overlayObjects);
        }
        if (overlayType != null) {
            scene.setOverlayType(overlayType);
        }
        if (shortLabelZh != null) {
            scene.setShortLabelZh(shortLabelZh);
        }
        if (sourceAspect != null) {
            scene.setSourceAspect(sourceAspect);
        }
        if (keyElements != null) {
            scene.setKeyElements(keyElements);
        }
    }
    
    public List<String> getKeyElements() {
        return keyElements;
    }
    
    public void setKeyElements(List<String> keyElements) {
        this.keyElements = keyElements;
    }
}
