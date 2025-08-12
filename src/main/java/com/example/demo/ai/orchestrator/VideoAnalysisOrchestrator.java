package com.example.demo.ai.orchestrator;

import com.example.demo.ai.template.SceneDetectionService;
import com.example.demo.model.SceneSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates video analysis by coordinating multiple Video Intelligence API calls
 * with quality gating and selective refinement for optimal results.
 */
@Service
public class VideoAnalysisOrchestrator {
    
    @Autowired
    private SceneDetectionService sceneDetectionService;
    
    @Autowired
    private VideoMetadataService videoMetadataService;
    
    @Autowired
    private FullPassAnalysisService fullPassAnalysisService;
    
    @Autowired
    private QualityGate qualityGate;
    
    @Autowired
    private RefinementWindowService refinementWindowService;
    
    @Value("${ai.vi.fullPass.maxSeconds:120}")
    private int fullPassMaxSeconds;
    
    /**
     * Analyzes a video using a multi-pass approach:
     * 1. Duration check: If video ≤ threshold, perform single comprehensive pass
     * 2. Coarse pass: Scene detection with person/label detection
     * 3. Quality assessment: Identify scenes needing refinement
     * 4. Refinement pass: Targeted object detection for low-quality scenes
     * 
     * @param gcsUri GCS URI of the video to analyze
     * @return List of scene segments with complete analysis
     */
    public List<SceneSegment> analyze(String gcsUri) {
        System.out.printf("VideoAnalysisOrchestrator: Starting analysis for %s%n", gcsUri);
        
        try {
            // Phase 0: Duration check for full pass rule
            int videoDurationSeconds = videoMetadataService.getVideoDurationSeconds(gcsUri);
            
            if (videoDurationSeconds > 0 && videoDurationSeconds <= fullPassMaxSeconds) {
                System.out.printf("VideoAnalysisOrchestrator: Video is %ds (≤ %ds), using full pass%n", 
                                videoDurationSeconds, fullPassMaxSeconds);
                return fullPassAnalysisService.performFullPassAnalysis(gcsUri);
            } else {
                System.out.printf("VideoAnalysisOrchestrator: Video is %ds (> %ds), using multi-pass approach%n", 
                                videoDurationSeconds, fullPassMaxSeconds);
            }
            
            // Phase 1: Coarse pass for longer videos
            List<SceneSegment> sceneSegments = performCoarsePass(gcsUri);
            
            if (sceneSegments.isEmpty()) {
                System.out.println("VideoAnalysisOrchestrator: No scenes detected in coarse pass");
                return new ArrayList<>();
            }
            
            System.out.printf("VideoAnalysisOrchestrator: Coarse pass completed with %d scenes%n", 
                             sceneSegments.size());
            
            // Phase 2: Quality assessment to identify scenes needing refinement
            QualityGate.QualityAssessment assessment = qualityGate.assessScenes(sceneSegments);
            
            if (!assessment.hasRefinementNeeds()) {
                System.out.println("VideoAnalysisOrchestrator: All scenes have good quality, no refinement needed");
                return sceneSegments;
            }
            
            // Phase 3: Create refinement windows for low-quality scenes  
            List<SegmentWindow> refinementWindows = refinementWindowService.createRefinementWindows(
                assessment.scenesNeedingRefinement, sceneSegments);
            
            if (refinementWindows.isEmpty()) {
                System.out.println("VideoAnalysisOrchestrator: No valid refinement windows, returning coarse results");
                return sceneSegments;
            }
            
            System.out.printf("VideoAnalysisOrchestrator: Created %d refinement windows for targeted analysis%n", 
                             refinementWindows.size());
            
            // Phase 4: Batched refinement call will be added in next PR
            // For now, log the windows that would be refined
            for (SegmentWindow window : refinementWindows) {
                System.out.printf("VideoAnalysisOrchestrator: Would refine %s%n", window);
            }
            
            return sceneSegments;
            
        } catch (Exception e) {
            System.err.printf("VideoAnalysisOrchestrator: Error analyzing video %s: %s%n", 
                            gcsUri, e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Performs the initial coarse analysis pass using existing scene detection.
     * This includes SHOT_CHANGE_DETECTION, PERSON_DETECTION, and optionally LABEL_DETECTION.
     * 
     * @param gcsUri GCS URI of the video
     * @return Scene segments from coarse analysis
     */
    private List<SceneSegment> performCoarsePass(String gcsUri) {
        System.out.println("VideoAnalysisOrchestrator: Performing coarse pass");
        
        // Delegate to existing scene detection service
        // This already includes SHOT + PERSON + LABEL (if enabled) + OBJECT_TRACKING
        List<SceneSegment> segments = sceneDetectionService.detectScenes(gcsUri);
        
        // Log coarse pass results
        int scenesWithObjects = 0;
        for (SceneSegment segment : segments) {
            if (segment.getOverlayObjects() != null && !segment.getOverlayObjects().isEmpty()) {
                scenesWithObjects++;
            }
        }
        
        System.out.printf("VideoAnalysisOrchestrator: Coarse pass found %d/%d scenes with objects%n",
                         scenesWithObjects, segments.size());
        
        return segments;
    }
}