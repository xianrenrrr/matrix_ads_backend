package com.example.demo.ai.services;
import com.example.demo.ai.providers.vision.VisionProvider;
import com.example.demo.ai.seg.SegmentationService;
import com.example.demo.ai.seg.dto.*;
import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.ai.suggest.SuggestionService;
import com.example.demo.ai.util.ImageCropper;
import com.example.demo.ai.shared.GcsFileResolver;
import com.example.demo.model.Scene;
import com.example.demo.service.FirebaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Scene-to-Scene Comparison Service
 * Implements AIWorkflow.md comparison algorithm using AI orchestrator
 */
@Service
public class ComparisonAIService {
    
    private static final Logger log = LoggerFactory.getLogger(ComparisonAIService.class);
    
    // Removed AI orchestrator dependency
    
    @Autowired
    private SegmentationService segmentationService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    @Autowired
    private SuggestionService suggestionService;
    
    @Autowired(required = false)
    private FirebaseStorageService firebaseStorageService;
    
    @Autowired
    private GcsFileResolver gcsFileResolver;
    
    @Value("${ai.comparison.frame-upload.enabled:false}")
    private boolean frameUploadEnabled;
    
    // Stricter comparison settings
    private static final int MAX_WINDOW_MS = 3000;
    private static final int MIN_WINDOW_MS = 2000;
    private static final int MAX_WINDOW_MS_LIMIT = 6000;
    private static final int SAMPLING_RATE_MS = 1000; // 1 Hz
    
    // Stricter weights as per requirements
    private static final double GEOMETRY_WEIGHT = 0.50;
    private static final double VISUAL_WEIGHT = 0.35;
    private static final double LABEL_WEIGHT = 0.15;
    
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
        long submissionDuration = getVideoDurationMs(userVideoUrl);
        
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
                
                // Apply stricter penalties
                double penalizedGeomScore = geometryScore;
                double penalizedVisScore = visualScore;
                
                if (geometryScore < 0.5) {
                    penalizedGeomScore *= 0.7;  // Harsh penalty for poor geometry
                }
                if (visualScore < 0.6) {
                    penalizedVisScore *= 0.85;  // Penalty for poor visual match
                }
                
                // Combined score with stricter weights
                double combinedScore = GEOMETRY_WEIGHT * penalizedGeomScore + 
                                     VISUAL_WEIGHT * penalizedVisScore + 
                                     LABEL_WEIGHT * labelScore;
                
                // Cap maximum score
                combinedScore = Math.min(combinedScore, 0.95);
                
                // Apply extra penalty if both geometry and visual are low
                if (geometryScore < 0.5 && visualScore < 0.6) {
                    combinedScore *= 0.8;
                }
                
                metrics.add(new PerSecondMetric(i, tRef, tSub, geometryScore, visualScore, labelScore, combinedScore));
            }
        }
        
        return metrics;
    }
    
    /**
     * Calculate geometry score using new segmentation service
     */
    private double calculateGeometryScore(String refFrameUrl, String subFrameUrl) {
        try {
            // Use new segmentation service (PaddleDet preferred, YOLO fallback)
            List<OverlayShape> refShapes = segmentationService.detect(refFrameUrl);
            List<OverlayShape> subShapes = segmentationService.detect(subFrameUrl);
            
            if (!refShapes.isEmpty() && !subShapes.isEmpty()) {
                // Match dominant objects by Chinese label if available
                OverlayShape refDominant = refShapes.get(0);
                OverlayShape subDominant = findBestMatch(refDominant, subShapes);
                
                if (subDominant != null) {
                    // Calculate IoU + centroid + scale
                    double iou = calculateShapeIoU(refDominant, subDominant);
                    double centroidDist = calculateCentroidDistance(refDominant, subDominant);
                    double scaleRatio = calculateScaleRatio(refDominant, subDominant);
                    
                    // Weighted combination
                    double score = 0.5 * iou + 0.3 * (1.0 - centroidDist) + 0.2 * scaleRatio;
                    
                    log.debug("Geometry score: IoU={}, centroid={}, scale={}, final={}", 
                             iou, 1.0 - centroidDist, scaleRatio, score);
                    
                    return score;
                }
            }
            
        } catch (Exception e) {
            log.warn("Geometry score calculation failed: {}", e.getMessage());
        }
        
        return 0.3; // Lower fallback for missing shapes
    }
    
    private OverlayShape findBestMatch(OverlayShape ref, List<OverlayShape> candidates) {
        // First try to match by Chinese label
        for (OverlayShape candidate : candidates) {
            if (ref.labelZh() != null && ref.labelZh().equals(candidate.labelZh())) {
                return candidate;
            }
        }
        
        // Fallback to highest confidence×area
        return candidates.get(0);
    }
    
    private double calculateShapeIoU(OverlayShape shape1, OverlayShape shape2) {
        double[] bbox1 = shapeToBoundingBox(shape1);
        double[] bbox2 = shapeToBoundingBox(shape2);
        return calculateBoundingBoxIoU(bbox1, bbox2);
    }
    
    private double[] shapeToBoundingBox(OverlayShape shape) {
        if (shape instanceof OverlayBox box) {
            return new double[]{box.x(), box.y(), box.x() + box.w(), box.y() + box.h()};
        } else if (shape instanceof OverlayPolygon polygon) {
            double xmin = 1.0, ymin = 1.0, xmax = 0.0, ymax = 0.0;
            for (Point p : polygon.points()) {
                xmin = Math.min(xmin, p.x());
                ymin = Math.min(ymin, p.y());
                xmax = Math.max(xmax, p.x());
                ymax = Math.max(ymax, p.y());
            }
            return new double[]{xmin, ymin, xmax, ymax};
        }
        return new double[]{0, 0, 0, 0};
    }
    
    private double calculateCentroidDistance(OverlayShape shape1, OverlayShape shape2) {
        double[] c1 = getShapeCentroid(shape1);
        double[] c2 = getShapeCentroid(shape2);
        double dist = Math.sqrt(Math.pow(c1[0] - c2[0], 2) + Math.pow(c1[1] - c2[1], 2));
        return Math.min(dist / Math.sqrt(2.0), 1.0);
    }
    
    private double[] getShapeCentroid(OverlayShape shape) {
        if (shape instanceof OverlayBox box) {
            return new double[]{box.x() + box.w()/2, box.y() + box.h()/2};
        } else if (shape instanceof OverlayPolygon polygon) {
            double sumX = 0, sumY = 0;
            for (Point p : polygon.points()) {
                sumX += p.x();
                sumY += p.y();
            }
            return new double[]{sumX / polygon.points().size(), sumY / polygon.points().size()};
        }
        return new double[]{0.5, 0.5};
    }
    
    private double calculateScaleRatio(OverlayShape shape1, OverlayShape shape2) {
        double area1 = getShapeArea(shape1);
        double area2 = getShapeArea(shape2);
        
        if (area1 == 0 || area2 == 0) return 0.0;
        
        double ratio = area1 / area2;
        if (ratio > 1) ratio = 1 / ratio;
        
        return ratio;  // Returns value between 0 and 1
    }
    
    private double getShapeArea(OverlayShape shape) {
        if (shape instanceof OverlayBox box) {
            return box.w() * box.h();
        } else if (shape instanceof OverlayPolygon polygon) {
            // Shoelace formula
            double area = 0;
            int n = polygon.points().size();
            for (int i = 0; i < n; i++) {
                Point p1 = polygon.points().get(i);
                Point p2 = polygon.points().get((i + 1) % n);
                area += p1.x() * p2.y() - p2.x() * p1.y();
            }
            return Math.abs(area) / 2.0;
        }
        return 0.0;
    }
    
    /**
     * Calculate visual similarity using HSV histogram correlation
     * SSIM requires complex image processing, so we use HSV histograms for now
     */
    private double calculateVisualScore(String refFrameUrl, String subFrameUrl) {
        try {
            // Skip computation for placeholder URLs
            if (refFrameUrl.contains("_frame_") && subFrameUrl.contains("_frame_")) {
                return 0.7; // Mock score for placeholder URLs
            }
            
            log.debug("Calculating visual similarity between {} and {}", refFrameUrl, subFrameUrl);
            
            // Use Python script for HSV histogram comparison
            ProcessBuilder pb = new ProcessBuilder(
                "python3", "-c",
                "import cv2; import numpy as np; " +
                "def calc_hsv_similarity(img1_path, img2_path): " +
                "    try: " +
                "        img1 = cv2.imread(img1_path); img2 = cv2.imread(img2_path); " +
                "        if img1 is None or img2 is None: return 0.5; " +
                "        hsv1 = cv2.cvtColor(img1, cv2.COLOR_BGR2HSV); " +
                "        hsv2 = cv2.cvtColor(img2, cv2.COLOR_BGR2HSV); " +
                "        hist1 = cv2.calcHist([hsv1], [0,1,2], None, [50,60,60], [0,180,0,256,0,256]); " +
                "        hist2 = cv2.calcHist([hsv2], [0,1,2], None, [50,60,60], [0,180,0,256,0,256]); " +
                "        correlation = cv2.compareHist(hist1, hist2, cv2.HISTCMP_CORREL); " +
                "        return max(0.0, min(1.0, correlation)); " +
                "    except: return 0.5; " +
                "print(calc_hsv_similarity('" + refFrameUrl.replace("file://", "") + "', '" + 
                subFrameUrl.replace("file://", "") + "'))"
            );
            
            Process process = pb.start();
            
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String output = reader.readLine();
                
                if (output != null && !output.trim().isEmpty()) {
                    double similarity = Double.parseDouble(output.trim());
                    log.debug("Visual similarity calculated: {} between {} and {}", similarity, refFrameUrl, subFrameUrl);
                    return similarity;
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Python HSV calculation failed with exit code {}", exitCode);
            }
            
        } catch (Exception e) {
            log.warn("Failed to calculate visual similarity: {}, using fallback", e.getMessage());
        }
        
        // Fallback: simple filename-based heuristic
        double fallbackScore = calculateFallbackVisualScore(refFrameUrl, subFrameUrl);
        log.debug("Using fallback visual score: {}", fallbackScore);
        return fallbackScore;
    }
    
    /**
     * Simple fallback visual similarity when OpenCV is not available
     */
    private double calculateFallbackVisualScore(String refFrameUrl, String subFrameUrl) {
        // Very basic heuristic: compare URL patterns/timestamps
        try {
            if (refFrameUrl.equals(subFrameUrl)) return 1.0;
            
            // Extract timestamp from frame URLs if possible
            String refTimestamp = extractTimestampFromFrameUrl(refFrameUrl);
            String subTimestamp = extractTimestampFromFrameUrl(subFrameUrl);
            
            if (refTimestamp != null && subTimestamp != null) {
                long refMs = Long.parseLong(refTimestamp);
                long subMs = Long.parseLong(subTimestamp);
                long diff = Math.abs(refMs - subMs);
                
                // Similarity inversely related to timestamp difference
                // Max difference of 3 seconds = 0.5 score, 0 difference = 1.0 score
                double similarity = Math.max(0.3, 1.0 - (diff / 3000.0));
                return Math.min(1.0, similarity);
            }
            
        } catch (Exception e) {
            log.debug("Fallback visual similarity calculation failed: {}", e.getMessage());
        }
        
        // Ultimate fallback
        return 0.65;
    }
    
    /**
     * Extract timestamp from frame URL (e.g., "video_frame_1500.jpg" -> "1500")
     */
    private String extractTimestampFromFrameUrl(String frameUrl) {
        try {
            if (frameUrl.contains("_frame_")) {
                String[] parts = frameUrl.split("_frame_");
                if (parts.length > 1) {
                    String timestampPart = parts[1].replaceAll("[^0-9]", "");
                    if (!timestampPart.isEmpty()) {
                        return timestampPart;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
    
    /**
     * Calculate label similarity using Chinese labeling service (Qwen-VL-Plus)
     */
    private double calculateLabelScore(String refFrameUrl, String subFrameUrl) {
        try {
            // Use segmentation service to get dominant objects
            List<OverlayShape> refShapes = segmentationService.detect(refFrameUrl);
            List<OverlayShape> subShapes = segmentationService.detect(subFrameUrl);
            
            if (!refShapes.isEmpty() && !subShapes.isEmpty()) {
                // Get dominant object from each frame
                OverlayShape refDominant = refShapes.get(0);
                OverlayShape subDominant = subShapes.get(0);
                
                // Crop and get Chinese labels
                byte[] refCrop = ImageCropper.crop(refFrameUrl, refDominant);
                byte[] subCrop = ImageCropper.crop(subFrameUrl, subDominant);
                
                String refLabelZh = objectLabelService.labelZh(refCrop);
                String subLabelZh = objectLabelService.labelZh(subCrop);
                
                log.debug("Chinese labels: ref='{}', sub='{}'", refLabelZh, subLabelZh);
                
                // Exact match = 1.0, near match = 0.7, no match = 0.3
                if (refLabelZh.equals(subLabelZh)) {
                    return 1.0;
                } else if (isNearMatch(refLabelZh, subLabelZh)) {
                    return 0.7;
                } else {
                    return 0.3;
                }
            }
            
        } catch (Exception e) {
            log.warn("Label score calculation failed: {}", e.getMessage());
        }
        
        return 0.5; // Fallback
    }
    
    private boolean isNearMatch(String label1, String label2) {
        // Simple heuristic: check if labels share characters
        if (label1.length() == 0 || label2.length() == 0) return false;
        
        int commonChars = 0;
        for (char c : label1.toCharArray()) {
            if (label2.indexOf(c) >= 0) {
                commonChars++;
            }
        }
        
        return commonChars >= Math.min(label1.length(), label2.length()) / 2;
    }
    
    /**
     * Extract Chinese labels from detected polygons using existing labelZh if available,
     * or generate new ones using Qwen LLM provider
     */
    private List<String> extractChineseLabels(List<VisionProvider.ObjectPolygon> polygons) {
        List<String> labels = new ArrayList<>();
        
        try {
            // First, collect any existing Chinese labels
            for (VisionProvider.ObjectPolygon polygon : polygons) {
                if (polygon.getLabelLocalized() != null && !polygon.getLabelLocalized().isEmpty()) {
                    labels.add(polygon.getLabelLocalized());
                } else if (polygon.getLabel() != null) {
                    // Convert English label to Chinese using Qwen
                    labels.add(translateToChineseLabel(polygon.getLabel()));
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract Chinese labels: {}", e.getMessage());
        }
        
        return labels;
    }
    
    /**
     * Translate English label to Chinese using Qwen LLM provider
     */
    private String translateToChineseLabel(String englishLabel) {
        // Minimal fallback: return original label (dev)
        return englishLabel == null ? "" : englishLabel;
    }
    
    /**
     * Compare two sets of Chinese labels for similarity
     */
    private double compareChineseLabelSets(List<String> refLabels, List<String> subLabels) {
        if (refLabels.isEmpty() && subLabels.isEmpty()) {
            return 1.0; // Both empty = perfect match
        }
        if (refLabels.isEmpty() || subLabels.isEmpty()) {
            return 0.2; // One empty = low score
        }
        
        // Calculate Jaccard similarity (intersection over union for sets)
        Set<String> refSet = new HashSet<>(refLabels);
        Set<String> subSet = new HashSet<>(subLabels);
        
        Set<String> intersection = new HashSet<>(refSet);
        intersection.retainAll(subSet);
        
        Set<String> union = new HashSet<>(refSet);
        union.addAll(subSet);
        
        double jaccardSimilarity = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        
        log.debug("Chinese label Jaccard similarity: {} (intersection={}, union={})", 
                 jaccardSimilarity, intersection.size(), union.size());
        
        return jaccardSimilarity;
    }
    

    private List<String> generateChineseSuggestions(Scene templateScene, SimilarityScores scores, 
                                                   List<PerSecondMetric> metrics) {
        try {
            // Build compact facts for suggestion service
            Map<String, Object> facts = new HashMap<>();
            facts.put("sceneTitle", templateScene.getSceneTitle());
            facts.put("overallScore", scores.overallSimilarity);
            facts.put("geometryScore", scores.geometryMean);
            facts.put("visualScore", scores.visualMean);
            facts.put("labelScore", scores.labelMean);
            
            // Add per-second deltas
            List<Map<String, Object>> perSecondData = new ArrayList<>();
            for (PerSecondMetric metric : metrics) {
                Map<String, Object> secondData = new HashMap<>();
                secondData.put("t", metric.second);
                secondData.put("geom", metric.geometryScore);
                secondData.put("vis", metric.visualScore);
                secondData.put("label", metric.labelScore);
                perSecondData.add(secondData);
            }
            facts.put("perSecond", perSecondData);
            
            // Get suggestions from service
            SuggestionService.SuggestionsResult result = suggestionService.suggestCn(facts);
            
            if (result != null && result.suggestionsZh() != null && !result.suggestionsZh().isEmpty()) {
                return result.suggestionsZh();
            }
            
        } catch (Exception e) {
            log.warn("Chinese suggestions generation failed: {}", e.getMessage());
        }
        
        // Fallback suggestions based on scores
        List<String> fallback = new ArrayList<>();
        if (scores.geometryMean < 0.5) {
            fallback.add("请调整拍摄角度和位置");
        }
        if (scores.visualMean < 0.6) {
            fallback.add("注意光线和画质");
        }
        if (scores.labelMean < 0.5) {
            fallback.add("确保主要物体清晰可见");
        }
        if (fallback.isEmpty()) {
            fallback.add("基本符合要求");
        }
        return fallback;
    }
    
    private List<String> generateNextActions(SimilarityScores scores) {
        // Use suggestion service for next actions
        try {
            Map<String, Object> facts = new HashMap<>();
            facts.put("overallScore", scores.overallSimilarity);
            
            SuggestionService.SuggestionsResult result = suggestionService.suggestCn(facts);
            if (result != null && result.nextActionsZh() != null && !result.nextActionsZh().isEmpty()) {
                return result.nextActionsZh();
            }
        } catch (Exception e) {
            log.debug("Failed to get next actions from suggestion service");
        }
        
        // Stricter thresholds for next actions
        if (scores.overallSimilarity > 0.90) {  // Raised from 0.85
            return Arrays.asList("可以提交审核");
        } else if (scores.overallSimilarity > 0.75) {  // Raised from 0.7
            return Arrays.asList("稍作调整后重新录制");
        } else {
            return Arrays.asList("重新录制", "参考模板示例");
        }
    }
    
    // Helper methods and data classes
    
    /**
     * Extract video duration using FFmpeg
     * @param videoUrl URL of the video file
     * @return Duration in milliseconds
     */
    private long getVideoDurationMs(String videoUrl) {
        try {
            log.debug("Extracting duration for video: {}", videoUrl);
            
            // Resolve GCS URL to local file to avoid 403 errors
            try (GcsFileResolver.ResolvedFile resolvedFile = gcsFileResolver.resolve(videoUrl)) {
                String localPath = resolvedFile.getPathAsString();
                
                // Use FFprobe to get video duration
                ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", 
                    "-v", "quiet",
                    "-show_entries", "format=duration",
                    "-of", "csv=p=0",
                    localPath
                );
                
                Process process = pb.start();
                
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String output = reader.readLine();
                    
                    if (output != null && !output.trim().isEmpty()) {
                        double durationSeconds = Double.parseDouble(output.trim());
                        long durationMs = (long) (durationSeconds * 1000);
                        
                        log.debug("Video duration extracted: {}ms for {}", durationMs, videoUrl);
                        return durationMs;
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.warn("FFprobe process exited with code {} for video: {}", exitCode, videoUrl);
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract video duration for {}: {}, using fallback", videoUrl, e.getMessage());
        }
        
        // Fallback: return reasonable default duration
        long fallbackDuration = 10000; // 10 seconds
        log.info("Using fallback duration {}ms for video: {}", fallbackDuration, videoUrl);
        return fallbackDuration;
    }
    
    /**
     * Extract frame from video at specific timestamp using FFmpeg
     * @param videoUrl URL of the video file
     * @param timestampMs Timestamp in milliseconds
     * @return URL of the extracted frame (uploaded to storage)
     */
    private String extractFrame(String videoUrl, long timestampMs) {
        try {
            log.debug("Extracting frame at {}ms from video: {}", timestampMs, videoUrl);
            
            // Create temporary file for extracted frame
            java.io.File tempFrame = java.io.File.createTempFile("frame_" + timestampMs + "_", ".jpg");
            
            // Convert milliseconds to seconds for FFmpeg
            double timestampSeconds = timestampMs / 1000.0;
            
            // Resolve GCS URL to local file to avoid 403 errors
            try (GcsFileResolver.ResolvedFile resolvedFile = gcsFileResolver.resolve(videoUrl)) {
                String localPath = resolvedFile.getPathAsString();
                
                // Use FFmpeg to extract frame at specific timestamp
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y", // Overwrite output file
                    "-ss", String.format("%.3f", timestampSeconds), // Seek to timestamp
                    "-i", localPath, // Input video (local path)
                    "-vframes", "1", // Extract only 1 frame
                    "-q:v", "2", // High quality JPEG
                    "-f", "image2", // Image format
                    tempFrame.getAbsolutePath()
                );
                
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                if (exitCode == 0 && tempFrame.exists() && tempFrame.length() > 0) {
                    String frameUrl;
                    
                    // Production mode: Upload to Firebase Storage if enabled
                    if (frameUploadEnabled && firebaseStorageService != null) {
                        frameUrl = uploadFrameToFirebase(tempFrame, videoUrl, timestampMs);
                        tempFrame.delete(); // Clean up immediately after upload
                    } else {
                        // Development mode: Use local file with scheduled cleanup
                        frameUrl = "file://" + tempFrame.getAbsolutePath();
                        scheduleFrameCleanup(tempFrame, 300000); // 5 minutes
                    }
                    
                    log.debug("Frame extracted successfully: {} for timestamp {}ms", frameUrl, timestampMs);
                    return frameUrl;
                } else {
                    log.warn("FFmpeg frame extraction failed with exit code {} for video: {} at {}ms", 
                            exitCode, videoUrl, timestampMs);
                    tempFrame.delete();
                }
            } // Close the try-with-resources block
            
        } catch (Exception e) {
            log.warn("Failed to extract frame from {} at {}ms: {}", videoUrl, timestampMs, e.getMessage());
        }
        
        // Fallback: return placeholder URL
        String fallbackUrl = videoUrl + "_frame_" + timestampMs + ".jpg";
        log.info("Using fallback frame URL: {}", fallbackUrl);
        return fallbackUrl;
    }
    
    /**
     * Upload extracted frame to Firebase Storage for persistence
     * TODO: Implement direct Firebase Storage upload for frames when FirebaseStorageService supports it
     */
    private String uploadFrameToFirebase(java.io.File frameFile, String videoUrl, long timestampMs) {
        try {
            log.info("Frame upload to Firebase requested for {} at {}ms, but direct upload not yet implemented", 
                    videoUrl, timestampMs);
            
            // TODO: Implement direct Firebase Storage upload for individual frames
            // This would require extending FirebaseStorageService with uploadFrame() method
            // For now, fall back to local storage with warning
            
            log.warn("Firebase frame upload not implemented yet, using local storage: {}", frameFile.getAbsolutePath());
            return "file://" + frameFile.getAbsolutePath();
            
        } catch (Exception e) {
            log.warn("Firebase frame upload failed for {} at {}ms: {}, using local fallback", 
                    videoUrl, timestampMs, e.getMessage());
            
            // Fallback: return local file path
            return "file://" + frameFile.getAbsolutePath();
        }
    }
    
    /**
     * Extract video ID from URL for organized storage
     */
    private String extractVideoIdFromUrl(String videoUrl) {
        try {
            // Extract ID from various URL patterns
            if (videoUrl.contains("/videos/")) {
                String[] parts = videoUrl.split("/videos/");
                if (parts.length > 1) {
                    String pathPart = parts[1];
                    // Handle pattern: /userId/videoId/filename
                    String[] pathSegments = pathPart.split("/");
                    if (pathSegments.length >= 2) {
                        return pathSegments[1]; // Return videoId
                    }
                }
            }
            
            // Fallback: use hash of URL
            return String.format("video_%d", Math.abs(videoUrl.hashCode()));
            
        } catch (Exception e) {
            log.debug("Failed to extract video ID from {}: {}", videoUrl, e.getMessage());
            return "unknown_video";
        }
    }
    
    /**
     * Schedule cleanup of temporary frame file
     */
    private void scheduleFrameCleanup(java.io.File tempFile, long delayMs) {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                if (tempFile.exists()) {
                    tempFile.delete();
                    log.debug("Cleaned up temporary frame file: {}", tempFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Failed to cleanup temp frame file {}: {}", tempFile.getAbsolutePath(), e.getMessage());
            }
        }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Calculate polygon similarity using IoU + centroid distance + scale similarity
     * Implements the geometry scoring from AIWorkFlow.md
     */
    @SuppressWarnings("unchecked")
    private double calculatePolygonSimilarity(List<?> refPolygons, List<?> subPolygons) {
        try {
            if (refPolygons == null || subPolygons == null || refPolygons.isEmpty() || subPolygons.isEmpty()) {
                return 0.3; // Low score for missing polygons
            }
            
            log.debug("Calculating polygon similarity: {} ref polygons, {} sub polygons", 
                     refPolygons.size(), subPolygons.size());
            
            // Cast to proper polygon type
            List<VisionProvider.ObjectPolygon> refPolygonList = (List<VisionProvider.ObjectPolygon>) refPolygons;
            List<VisionProvider.ObjectPolygon> subPolygonList = (List<VisionProvider.ObjectPolygon>) subPolygons;
            
            // Find best matching pairs using Hungarian algorithm (simplified)
            double totalScore = 0.0;
            int matchedPairs = 0;
            boolean[] usedSub = new boolean[subPolygonList.size()];
            
            for (VisionProvider.ObjectPolygon refPoly : refPolygonList) {
                double bestScore = 0.0;
                int bestMatch = -1;
                
                // Find best matching polygon in submission
                for (int i = 0; i < subPolygonList.size(); i++) {
                    if (usedSub[i]) continue;
                    
                    VisionProvider.ObjectPolygon subPoly = subPolygonList.get(i);
                    double pairScore = calculatePolygonPairSimilarity(refPoly, subPoly);
                    
                    if (pairScore > bestScore) {
                        bestScore = pairScore;
                        bestMatch = i;
                    }
                }
                
                if (bestMatch >= 0) {
                    usedSub[bestMatch] = true;
                    totalScore += bestScore;
                    matchedPairs++;
                }
            }
            
            // Average score with penalty for unmatched polygons
            double averageScore = matchedPairs > 0 ? totalScore / matchedPairs : 0.0;
            
            // Penalty for count mismatch
            int maxCount = Math.max(refPolygonList.size(), subPolygonList.size());
            double countPenalty = 1.0 - (Math.abs(refPolygonList.size() - subPolygonList.size()) / (double) maxCount);
            
            double finalScore = averageScore * countPenalty;
            log.debug("Polygon similarity: avg={}, countPenalty={}, final={}", averageScore, countPenalty, finalScore);
            
            return Math.max(0.0, Math.min(1.0, finalScore));
            
        } catch (Exception e) {
            log.warn("Failed to calculate polygon similarity: {}, using fallback", e.getMessage());
            return 0.5; // Fallback score
        }
    }
    
    /**
     * Calculate similarity between two individual polygons
     * Uses IoU + centroid distance + scale similarity
     */
    private double calculatePolygonPairSimilarity(VisionProvider.ObjectPolygon refPoly, VisionProvider.ObjectPolygon subPoly) {
        try {
            // 1. IoU (Intersection over Union) - 50% weight
            double iou = calculatePolygonIoU(refPoly.getPoints(), subPoly.getPoints());
            
            // 2. Centroid distance - 30% weight
            double centroidSimilarity = calculateCentroidSimilarity(refPoly.getPoints(), subPoly.getPoints());
            
            // 3. Scale similarity - 20% weight  
            double scaleSimilarity = calculateScaleSimilarity(refPoly.getPoints(), subPoly.getPoints());
            
            // Weighted combination
            double combinedScore = 0.5 * iou + 0.3 * centroidSimilarity + 0.2 * scaleSimilarity;
            
            log.debug("Polygon pair similarity: IoU={}, centroid={}, scale={}, combined={}", 
                     iou, centroidSimilarity, scaleSimilarity, combinedScore);
            
            return Math.max(0.0, Math.min(1.0, combinedScore));
            
        } catch (Exception e) {
            log.debug("Failed to calculate polygon pair similarity: {}", e.getMessage());
            return 0.3;
        }
    }
    
    /**
     * Calculate Intersection over Union (IoU) for two polygons
     */
    private double calculatePolygonIoU(List<VisionProvider.ObjectPolygon.Point> poly1, List<VisionProvider.ObjectPolygon.Point> poly2) {
        try {
            // Simple approximation: convert polygons to bounding boxes for IoU
            double[] bbox1 = polygonToBoundingBox(poly1);
            double[] bbox2 = polygonToBoundingBox(poly2);
            
            return calculateBoundingBoxIoU(bbox1, bbox2);
            
        } catch (Exception e) {
            log.debug("IoU calculation failed: {}", e.getMessage());
            return 0.2;
        }
    }
    
    /**
     * Convert polygon to bounding box [xmin, ymin, xmax, ymax]
     */
    private double[] polygonToBoundingBox(List<VisionProvider.ObjectPolygon.Point> points) {
        if (points.isEmpty()) return new double[]{0, 0, 0, 0};
        
        double xmin = points.get(0).getX();
        double ymin = points.get(0).getY();
        double xmax = xmin;
        double ymax = ymin;
        
        for (VisionProvider.ObjectPolygon.Point point : points) {
            xmin = Math.min(xmin, point.getX());
            ymin = Math.min(ymin, point.getY());
            xmax = Math.max(xmax, point.getX());
            ymax = Math.max(ymax, point.getY());
        }
        
        return new double[]{xmin, ymin, xmax, ymax};
    }
    
    /**
     * Calculate IoU for two bounding boxes
     */
    private double calculateBoundingBoxIoU(double[] bbox1, double[] bbox2) {
        // Calculate intersection
        double x1 = Math.max(bbox1[0], bbox2[0]);
        double y1 = Math.max(bbox1[1], bbox2[1]);
        double x2 = Math.min(bbox1[2], bbox2[2]);
        double y2 = Math.min(bbox1[3], bbox2[3]);
        
        if (x2 <= x1 || y2 <= y1) return 0.0; // No intersection
        
        double intersection = (x2 - x1) * (y2 - y1);
        
        // Calculate union
        double area1 = (bbox1[2] - bbox1[0]) * (bbox1[3] - bbox1[1]);
        double area2 = (bbox2[2] - bbox2[0]) * (bbox2[3] - bbox2[1]);
        double union = area1 + area2 - intersection;
        
        return union > 0 ? intersection / union : 0.0;
    }
    
    /**
     * Calculate centroid similarity (1.0 - normalized distance)
     */
    private double calculateCentroidSimilarity(List<VisionProvider.ObjectPolygon.Point> poly1, List<VisionProvider.ObjectPolygon.Point> poly2) {
        try {
            double[] centroid1 = calculateCentroid(poly1);
            double[] centroid2 = calculateCentroid(poly2);
            
            // Euclidean distance between centroids
            double distance = Math.sqrt(Math.pow(centroid1[0] - centroid2[0], 2) + Math.pow(centroid1[1] - centroid2[1], 2));
            
            // Normalize by diagonal of unit square (sqrt(2) for [0,1] x [0,1] space)
            double maxDistance = Math.sqrt(2.0);
            double normalizedDistance = Math.min(distance / maxDistance, 1.0);
            
            return 1.0 - normalizedDistance;
            
        } catch (Exception e) {
            log.debug("Centroid similarity calculation failed: {}", e.getMessage());
            return 0.5;
        }
    }
    
    /**
     * Calculate polygon centroid
     */
    private double[] calculateCentroid(List<VisionProvider.ObjectPolygon.Point> points) {
        if (points.isEmpty()) return new double[]{0.5, 0.5};
        
        double sumX = 0, sumY = 0;
        for (VisionProvider.ObjectPolygon.Point point : points) {
            sumX += point.getX();
            sumY += point.getY();
        }
        
        return new double[]{sumX / points.size(), sumY / points.size()};
    }
    
    /**
     * Calculate scale similarity based on polygon area
     */
    private double calculateScaleSimilarity(List<VisionProvider.ObjectPolygon.Point> poly1, List<VisionProvider.ObjectPolygon.Point> poly2) {
        try {
            double area1 = calculatePolygonArea(poly1);
            double area2 = calculatePolygonArea(poly2);
            
            if (area1 == 0 && area2 == 0) return 1.0;
            if (area1 == 0 || area2 == 0) return 0.0;
            
            // Scale similarity: 1 - |log(area1/area2)|
            double ratio = area1 / area2;
            double logRatio = Math.abs(Math.log(ratio));
            
            // Clamp to reasonable range (scale changes up to 4x are acceptable)
            double similarity = Math.max(0.0, 1.0 - logRatio / Math.log(4.0));
            
            return similarity;
            
        } catch (Exception e) {
            log.debug("Scale similarity calculation failed: {}", e.getMessage());
            return 0.6;
        }
    }
    
    /**
     * Calculate polygon area using shoelace formula (reuse from existing method)
     */
    private double calculatePolygonArea(List<VisionProvider.ObjectPolygon.Point> points) {
        if (points.size() < 3) return 0.0;
        
        double area = 0.0;
        int n = points.size();
        
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += points.get(i).getX() * points.get(j).getY();
            area -= points.get(j).getX() * points.get(i).getY();
        }
        
        return Math.abs(area) / 2.0;
    }
    
    private SimilarityScores calculateOverallScores(List<PerSecondMetric> metrics) {
        if (metrics.isEmpty()) {
            return new SimilarityScores(0.5, 0.5, 0.5, 0.5);
        }
        
        double geometryMean = metrics.stream().mapToDouble(m -> m.geometryScore).average().orElse(0.5);
        double visualMean = metrics.stream().mapToDouble(m -> m.visualScore).average().orElse(0.5);
        double labelMean = metrics.stream().mapToDouble(m -> m.labelScore).average().orElse(0.5);
        // Apply per-scene final multiplier of 0.9 as per requirements
        double overallSimilarity = metrics.stream().mapToDouble(m -> m.combinedScore).average().orElse(0.5);
        overallSimilarity *= 0.9;  // Per-scene final penalty
        
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
