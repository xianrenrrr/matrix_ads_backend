package com.example.demo.ai.services;

import com.example.demo.ai.core.AIModelType;
import com.example.demo.ai.providers.llm.LLMProvider;
import com.example.demo.model.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Scene-to-Scene Comparison Service
 * Implements AIWorkflow.md comparison algorithm using AI orchestrator
 */
@Service
public class ComparisonAIService {
    
    private static final Logger log = LoggerFactory.getLogger(ComparisonAIService.class);
    
    @Autowired
    private AIOrchestrator aiOrchestrator;
    
    // Default comparison settings
    private static final int MAX_WINDOW_MS = 3000;
    private static final int MIN_WINDOW_MS = 2000;
    private static final int MAX_WINDOW_MS_LIMIT = 6000;
    private static final int SAMPLING_RATE_MS = 1000; // 1 Hz
    
    /**
     * Compare user-submitted scene with template scene
     * @param templateScene Template scene with timing information
     * @param userVideoUrl User's uploaded scene video URL
     * @param templateVideoUrl Original template video URL
     * @return Comparison result with similarity score and suggestions
     */
    public SceneComparisonResult compareScenes(Scene templateScene, String userVideoUrl, String templateVideoUrl) {
        log.info("Comparing scene {} - Template: {} with User: {}", 
                templateScene.getSceneNumber(), templateVideoUrl, userVideoUrl);
        
        try {
            // Step 1: Window selection as per AIWorkflow.md
            ComparisonWindow window = calculateComparisonWindow(templateScene, userVideoUrl);
            
            // Step 2: Per-second metrics calculation
            List<PerSecondMetric> perSecondMetrics = calculatePerSecondMetrics(
                templateVideoUrl, userVideoUrl, window);
            
            // Step 3: Overall scoring
            SimilarityScores scores = calculateOverallScores(perSecondMetrics);
            
            // Step 4: Chinese suggestions using Qwen
            List<String> suggestions = generateChineseSuggestions(templateScene, scores, perSecondMetrics);
            List<String> nextActions = generateNextActions(scores);
            
            return new SceneComparisonResult(
                scores.overallSimilarity,
                scores.geometryMean,
                scores.visualMean, 
                scores.labelMean,
                perSecondMetrics,
                suggestions,
                nextActions
            );
            
        } catch (Exception e) {
            log.error("Scene comparison failed for scene {}: {}", 
                     templateScene.getSceneNumber(), e.getMessage(), e);
            return createFallbackResult();
        }
    }
    
    /**
     * Calculate comparison window as per AIWorkflow.md
     */
    private ComparisonWindow calculateComparisonWindow(Scene templateScene, String userVideoUrl) {
        // Reference window: scene's FFmpeg segment {refStartMs, refEndMs}
        long refStartMs = templateScene.getStartTimeMs() != null ? templateScene.getStartTimeMs() : 0L;
        long refEndMs = templateScene.getEndTimeMs() != null ? templateScene.getEndTimeMs() : 5000L;
        long refDuration = refEndMs - refStartMs;
        
        // Submission window: entire submission clip (starts at 0)
        long submissionDuration = getVideoDurationMs(userVideoUrl); // TODO: Implement
        
        // Duration: windowMs = min(refDuration, submissionDuration, maxWindowMs)
        long windowMs = Math.min(Math.min(refDuration, submissionDuration), MAX_WINDOW_MS);
        windowMs = Math.max(windowMs, MIN_WINDOW_MS); // Clamp to [2000, 6000]
        windowMs = Math.min(windowMs, MAX_WINDOW_MS_LIMIT);
        
        int samplingCount = (int) (windowMs / SAMPLING_RATE_MS);
        
        log.info("Comparison window: ref={}ms-{}ms, sub=0-{}ms, window={}ms, samples={}", 
                refStartMs, refEndMs, submissionDuration, windowMs, samplingCount);
        
        return new ComparisonWindow(refStartMs, refEndMs, 0, windowMs, samplingCount);
    }
    
    /**
     * Calculate per-second metrics using AI providers
     */
    private List<PerSecondMetric> calculatePerSecondMetrics(String templateVideoUrl, 
                                                           String userVideoUrl, 
                                                           ComparisonWindow window) {
        List<PerSecondMetric> metrics = new ArrayList<>();
        
        for (int i = 0; i < window.samplingCount; i++) {
            long tRef = window.refStartMs + i * SAMPLING_RATE_MS;
            long tSub = window.subStartMs + i * SAMPLING_RATE_MS;
            
            // Extract frames at tRef and tSub
            String refFrameUrl = extractFrame(templateVideoUrl, tRef);
            String subFrameUrl = extractFrame(userVideoUrl, tSub);
            
            if (refFrameUrl != null && subFrameUrl != null) {
                // Geometry comparison using YOLO (via AI orchestrator)
                double geometryScore = calculateGeometryScore(refFrameUrl, subFrameUrl);
                
                // Visual comparison using SSIM/HSV
                double visualScore = calculateVisualScore(refFrameUrl, subFrameUrl);
                
                // Label comparison using Qwen (if needed)
                double labelScore = calculateLabelScore(refFrameUrl, subFrameUrl);
                
                // Combined score: 0.45*geom + 0.40*vis + 0.15*label
                double combinedScore = 0.45 * geometryScore + 0.40 * visualScore + 0.15 * labelScore;
                
                metrics.add(new PerSecondMetric(i, tRef, tSub, geometryScore, visualScore, labelScore, combinedScore));
            }
        }
        
        return metrics;
    }
    
    /**
     * Calculate geometry score using YOLO segmentation (via AI orchestrator)
     */
    private double calculateGeometryScore(String refFrameUrl, String subFrameUrl) {
        try {
            // Get object polygons from both frames using YOLO -> Google Vision fallback
            var refResponse = aiOrchestrator.executeWithFallback(
                AIModelType.VISION, "detectObjectPolygons",
                provider -> ((com.example.demo.ai.providers.vision.VisionProvider) provider)
                    .detectObjectPolygons(refFrameUrl)
            );
            
            var subResponse = aiOrchestrator.executeWithFallback(
                AIModelType.VISION, "detectObjectPolygons", 
                provider -> ((com.example.demo.ai.providers.vision.VisionProvider) provider)
                    .detectObjectPolygons(subFrameUrl)
            );
            
            if (refResponse.isSuccess() && subResponse.isSuccess()) {
                var refPolygons = refResponse.getData();
                var subPolygons = subResponse.getData();
                
                // Calculate IoU + centroid + scale matching
                return calculatePolygonSimilarity(refPolygons, subPolygons);
            }
            
        } catch (Exception e) {
            log.warn("Geometry score calculation failed: {}", e.getMessage());
        }
        
        return 0.5; // Fallback score
    }
    
    /**
     * Calculate visual similarity using SSIM or HSV histogram correlation
     */
    private double calculateVisualScore(String refFrameUrl, String subFrameUrl) {
        // TODO: Implement SSIM or HSV histogram comparison
        // For now, return mock score
        return 0.7;
    }
    
    /**
     * Calculate label similarity using Chinese text comparison (Qwen)
     */
    private double calculateLabelScore(String refFrameUrl, String subFrameUrl) {
        // TODO: Implement Chinese label comparison using Qwen
        // For now, return mock score  
        return 0.6;
    }
    
    /**
     * Generate Chinese suggestions using Qwen
     */
    private List<String> generateChineseSuggestions(Scene templateScene, SimilarityScores scores, 
                                                   List<PerSecondMetric> metrics) {
        try {
            var request = new LLMProvider.SceneSuggestionsRequest();
            request.setSimilarityScore(scores.overallSimilarity);
            request.setSceneTitle(templateScene.getSceneTitle());
            request.setAnalysisData(buildAnalysisData(scores, metrics));
            
            var response = aiOrchestrator.executeWithFallback(
                AIModelType.LLM, "generateSceneSuggestions",
                provider -> ((LLMProvider) provider).generateSceneSuggestions(request)
            );
            
            if (response.isSuccess() && response.getData() != null) {
                return response.getData().getSuggestionsZh();
            }
            
        } catch (Exception e) {
            log.warn("Chinese suggestions generation failed: {}", e.getMessage());
        }
        
        // Fallback suggestions
        return Arrays.asList("画质良好", "构图需要调整");
    }
    
    private List<String> generateNextActions(SimilarityScores scores) {
        if (scores.overallSimilarity > 0.85) {
            return Arrays.asList("可以提交审核");
        } else if (scores.overallSimilarity > 0.7) {
            return Arrays.asList("稍作调整后重新录制");
        } else {
            return Arrays.asList("重新录制", "参考模板示例");
        }
    }
    
    // Helper methods and data classes
    
    private long getVideoDurationMs(String videoUrl) {
        // TODO: Implement video duration extraction
        return 5000; // Mock 5 seconds
    }
    
    private String extractFrame(String videoUrl, long timestampMs) {
        // TODO: Implement frame extraction
        return videoUrl + "_frame_" + timestampMs;
    }
    
    private double calculatePolygonSimilarity(List<?> refPolygons, List<?> subPolygons) {
        // TODO: Implement IoU + centroid + scale calculation
        return 0.75;
    }
    
    private SimilarityScores calculateOverallScores(List<PerSecondMetric> metrics) {
        if (metrics.isEmpty()) {
            return new SimilarityScores(0.5, 0.5, 0.5, 0.5);
        }
        
        double geometryMean = metrics.stream().mapToDouble(m -> m.geometryScore).average().orElse(0.5);
        double visualMean = metrics.stream().mapToDouble(m -> m.visualScore).average().orElse(0.5);
        double labelMean = metrics.stream().mapToDouble(m -> m.labelScore).average().orElse(0.5);
        double overallSimilarity = metrics.stream().mapToDouble(m -> m.combinedScore).average().orElse(0.5);
        
        return new SimilarityScores(overallSimilarity, geometryMean, visualMean, labelMean);
    }
    
    private Map<String, Object> buildAnalysisData(SimilarityScores scores, List<PerSecondMetric> metrics) {
        Map<String, Object> data = new HashMap<>();
        data.put("overallScore", scores.overallSimilarity);
        data.put("geometryScore", scores.geometryMean);
        data.put("visualScore", scores.visualMean);
        data.put("sampleCount", metrics.size());
        return data;
    }
    
    private SceneComparisonResult createFallbackResult() {
        return new SceneComparisonResult(
            0.75, 0.7, 0.8, 0.6,
            new ArrayList<>(),
            Arrays.asList("无法进行AI分析", "请检查视频质量"),
            Arrays.asList("重新上传视频")
        );
    }
    
    // Data classes
    
    public static class ComparisonWindow {
        public final long refStartMs, refEndMs, subStartMs, windowMs;
        public final int samplingCount;
        
        public ComparisonWindow(long refStartMs, long refEndMs, long subStartMs, long windowMs, int samplingCount) {
            this.refStartMs = refStartMs;
            this.refEndMs = refEndMs;
            this.subStartMs = subStartMs;
            this.windowMs = windowMs;
            this.samplingCount = samplingCount;
        }
    }
    
    public static class PerSecondMetric {
        public final int second;
        public final long tRefMs, tSubMs;
        public final double geometryScore, visualScore, labelScore, combinedScore;
        
        public PerSecondMetric(int second, long tRefMs, long tSubMs, 
                              double geometryScore, double visualScore, double labelScore, double combinedScore) {
            this.second = second;
            this.tRefMs = tRefMs;
            this.tSubMs = tSubMs;
            this.geometryScore = geometryScore;
            this.visualScore = visualScore;
            this.labelScore = labelScore;
            this.combinedScore = combinedScore;
        }
    }
    
    public static class SimilarityScores {
        public final double overallSimilarity, geometryMean, visualMean, labelMean;
        
        public SimilarityScores(double overallSimilarity, double geometryMean, double visualMean, double labelMean) {
            this.overallSimilarity = overallSimilarity;
            this.geometryMean = geometryMean;
            this.visualMean = visualMean;
            this.labelMean = labelMean;
        }
    }
    
    public static class SceneComparisonResult {
        public final double similarityScore;
        public final double geometryMean, visualMean, labelMean;
        public final List<PerSecondMetric> perSecondMetrics;
        public final List<String> suggestions;
        public final List<String> nextActions;
        
        public SceneComparisonResult(double similarityScore, double geometryMean, double visualMean, double labelMean,
                                   List<PerSecondMetric> perSecondMetrics, List<String> suggestions, List<String> nextActions) {
            this.similarityScore = similarityScore;
            this.geometryMean = geometryMean;
            this.visualMean = visualMean;
            this.labelMean = labelMean;
            this.perSecondMetrics = perSecondMetrics;
            this.suggestions = suggestions;
            this.nextActions = nextActions;
        }
    }
}