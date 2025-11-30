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
    private String shortLabelZh;
    private String sourceAspect;  // e.g., "9:16" or "16:9"
    
    // Key elements with optional bounding boxes (unified system)
    private List<Scene.KeyElement> keyElementsWithBoxes;
    
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
    
    public List<Scene.KeyElement> getKeyElementsWithBoxes() {
        return keyElementsWithBoxes;
    }
    
    public void setKeyElementsWithBoxes(List<Scene.KeyElement> keyElementsWithBoxes) {
        this.keyElementsWithBoxes = keyElementsWithBoxes;
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
        if (shortLabelZh != null) {
            scene.setShortLabelZh(shortLabelZh);
        }
        if (sourceAspect != null) {
            scene.setSourceAspect(sourceAspect);
        }
        // Apply keyElementsWithBoxes
        if (keyElementsWithBoxes != null) {
            scene.setKeyElementsWithBoxes(keyElementsWithBoxes);
        }
    }
    
    // REMOVED - use getKeyElementsWithBoxes() instead
    /*
    public List<String> getKeyElements() {
        return keyElements;
    }
    
    public void setKeyElements(List<String> keyElements) {
        this.keyElements = keyElements;
    }
    */
}
