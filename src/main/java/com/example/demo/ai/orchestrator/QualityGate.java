package com.example.demo.ai.orchestrator;

import com.example.demo.model.SceneSegment;
import com.example.demo.model.Scene.ObjectOverlay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Quality gate service for assessing scene analysis quality and determining
 * which scenes need refinement through targeted object detection.
 */
@Service
public class QualityGate {
    
    @Value("${ai.object.confidenceThreshold:0.6}")
    private float confidenceThreshold;
    
    @Value("${ai.object.minArea:0.02}")
    private float minArea;
    
    /**
     * Evaluates whether a scene has usable object detection results.
     * A scene is considered to have good quality if it has objects with:
     * - Average confidence ≥ threshold
     * - At least one object with area ≥ minimum area
     * - Objects detected at all
     * 
     * @param scene Scene segment to evaluate
     * @return true if scene has good object detection quality, false if needs refinement
     */
    public boolean hasUsableObjects(SceneSegment scene) {
        List<ObjectOverlay> overlays = scene.getOverlayObjects();
        
        if (overlays == null || overlays.isEmpty()) {
            System.out.printf("QualityGate: Scene lacks objects (empty overlay list)%n");
            return false;
        }
        
        // Check average confidence
        float totalConfidence = 0;
        int validObjects = 0;
        boolean hasMinAreaObject = false;
        
        for (ObjectOverlay overlay : overlays) {
            float confidence = overlay.getConfidence();
            float area = overlay.getWidth() * overlay.getHeight();
            
            totalConfidence += confidence;
            validObjects++;
            
            if (area >= minArea) {
                hasMinAreaObject = true;
            }
        }
        
        if (validObjects == 0) {
            System.out.printf("QualityGate: Scene has no valid objects%n");
            return false;
        }
        
        float avgConfidence = totalConfidence / validObjects;
        
        boolean meetsConfidenceThreshold = avgConfidence >= confidenceThreshold;
        boolean meetsAreaThreshold = hasMinAreaObject;
        
        System.out.printf("QualityGate: Scene evaluation - avgConf=%.2f (≥%.2f: %s), hasMinArea=%s%n", 
                         avgConfidence, confidenceThreshold, meetsConfidenceThreshold, meetsAreaThreshold);
        
        return meetsConfidenceThreshold && meetsAreaThreshold;
    }
    
    /**
     * Determines overall quality assessment for a list of scenes.
     * 
     * @param scenes List of scene segments to evaluate
     * @return QualityAssessment with statistics and scenes needing refinement
     */
    public QualityAssessment assessScenes(List<SceneSegment> scenes) {
        QualityAssessment assessment = new QualityAssessment();
        
        for (SceneSegment scene : scenes) {
            boolean hasUsable = hasUsableObjects(scene);
            
            if (hasUsable) {
                assessment.goodQualityScenes++;
            } else {
                assessment.needsRefinementScenes++;
                assessment.scenesNeedingRefinement.add(scene);
            }
        }
        
        assessment.totalScenes = scenes.size();
        
        System.out.printf("QualityGate: Assessment complete - %d good, %d need refinement out of %d total%n",
                         assessment.goodQualityScenes, assessment.needsRefinementScenes, assessment.totalScenes);
        
        return assessment;
    }
    
    /**
     * Quality assessment results
     */
    public static class QualityAssessment {
        public int totalScenes = 0;
        public int goodQualityScenes = 0;
        public int needsRefinementScenes = 0;
        public List<SceneSegment> scenesNeedingRefinement = new java.util.ArrayList<>();
        
        public boolean hasRefinementNeeds() {
            return needsRefinementScenes > 0;
        }
        
        public double getRefinementRate() {
            return totalScenes > 0 ? (double) needsRefinementScenes / totalScenes : 0.0;
        }
    }
}