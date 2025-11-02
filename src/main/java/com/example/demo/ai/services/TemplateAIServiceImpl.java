package com.example.demo.ai.services;

import com.example.demo.ai.subtitle.AzureVideoIndexerExtractor;
import com.example.demo.ai.subtitle.SubtitleSegment;
import com.example.demo.ai.label.ObjectLabelService;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.Video;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.time.Duration;
import java.util.*;

/**
 * AI Template Creation Service - Azure Video Indexer Architecture v4.0
 * 
 * This service creates video templates using Azure Video Indexer for comprehensive
 * video analysis and Qwen VL for scene understanding and object grounding.
 * 
 * Architecture:
 * 1. Azure Video Indexer - Single API call returns:
 *    - Transcript (speech-to-text with 95%+ accuracy)
 *    - OCR (on-screen text with 98%+ accuracy)
 *    - Scenes (semantic scene boundaries)
 *    - Shots & Keyframes (camera shots with representative frames)
 *    - Labels (visual content labels)
 *    - Detected Objects (object types without bounding boxes)
 * 
 * 2. Scene Processing:
 *    - Use Azure scenes as timing boundaries
 *    - Combine transcript + OCR for scriptLines (no AI cleaning needed!)
 *    - Extract keyframes from Azure shots
 * 
 * 3. Qwen VL Grounding (replaces unreliable YOLO):
 *    - Azure detects objects: ["汽车", "人", "书"]
 *    - Qwen VL grounds them: Returns bounding boxes + scene analysis
 *    - More reliable, context-aware, native Chinese support
 * 
 * 4. Purpose Identification:
 *    - Analyze all scriptLines + Azure labels
 *    - Identify video purpose (e.g., "车衣贴膜")
 * 
 * 5. Template Metadata:
 *    - Generate guidance using Qwen reasoning
 *    - Per-scene instructions for content creators
 * 
 * Benefits over v3.0 (Alibaba):
 * - Better quality: 95%+ transcript vs 70%, 98%+ OCR vs 20%
 * - Simpler: 1 API call vs 3+ separate services
 * - More reliable: No YOLO failures, stable Azure + Qwen
 * - Cleaner data: No AI cleaning needed for transcripts
 * 
 * @see docs/AI_SYSTEM_ARCHITECTURE_v4.0_AZURE.md
 */
@Service
public class TemplateAIServiceImpl implements TemplateAIService {
    
    private static final Logger log = LoggerFactory.getLogger(TemplateAIServiceImpl.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Autowired
    private AzureVideoIndexerExtractor azureExtractor;
    
    @Autowired
    private UnifiedSceneAnalysisService sceneAnalysisService;
    
    @Autowired
    private ObjectLabelService objectLabelService;
    
    /**
     * Generate template from video (interface method)
     */
    @Override
    public ManualTemplate generateTemplate(Video video) {
        return generateTemplate(video, "zh-CN", null);
    }
    
    /**
     * Generate template from video with language and description (interface method)
     * 
     * Flow:
     * 1. Azure Video Indexer - Get all video insights
     * 2. Create scenes from Azure scene boundaries
     * 3. Assign scriptLines (transcript + OCR)
     * 4. Qwen VL analysis per scene (grounding + analysis)
     * 5. Purpose identification
     * 6. Generate template metadata
     */
    @Override
    public ManualTemplate generateTemplate(Video video, String language, String userDescription) {
        log.info("=== Starting AI Template Creation (Azure v4.0) ===");
        log.info("Video ID: {}", video.getId());
        log.info("Video URL: {}", video.getUrl());
        log.info("Language: {}", language);
        log.info("User Description: {}", userDescription);
        
        ManualTemplate template = new ManualTemplate();
        template.setVideoId(video.getId());
        template.setUserId(video.getUserId());
        
        try {
            String videoUrl = video.getUrl();
            
            // Step 1: Azure Video Indexer - Single comprehensive call
            log.info("[STEP 1] Calling Azure Video Indexer...");
            com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureVideoIndexerResult azureResult = indexVideoWithAzure(videoUrl);
            
            if (azureResult == null) {
                log.error("Azure Video Indexer failed");
                return template;
            }
            
            log.info("✅ Azure indexing complete:");
            log.info("  - Transcript segments: {}", azureResult.transcript.size());
            log.info("  - OCR segments: {}", azureResult.ocr.size());
            log.info("  - Scenes: {}", azureResult.scenes.size());
            log.info("  - Shots: {}", azureResult.shots.size());
            log.info("  - Labels: {}", azureResult.labels.size());
            log.info("  - Detected objects: {}", azureResult.detectedObjects.size());
            
            // Step 2: Create scenes from Azure scene boundaries
            log.info("[STEP 2] Creating scenes from Azure boundaries...");
            List<Scene> scenes = createScenesFromAzure(azureResult.scenes);
            log.info("✅ Created {} scenes", scenes.size());
            
            // Step 3: Assign scriptLines (combine transcript + OCR)
            log.info("[STEP 3] Assigning scriptLines to scenes...");
            assignScriptLines(scenes, azureResult.transcript, azureResult.ocr);
            logScriptLines(scenes);
            
            // Step 4: Qwen VL analysis per scene
            log.info("[STEP 4] Analyzing scenes with Qwen VL...");
            analyzeScenes(scenes, videoUrl, azureResult, language);
            
            // Step 5: Generate template metadata (includes videoPurpose from Qwen)
            log.info("[STEP 5] Generating template metadata with Qwen...");
            generateMetadata(template, scenes, userDescription, video);
            
            // Set locale used for template creation
            template.setLocaleUsed(language);
            
            template.setScenes(scenes);
            log.info("=== AI Template Creation Complete ===");
            log.info("Template: {} scenes, purpose: {}, locale: {}", scenes.size(), template.getVideoPurpose(), language);
            
        } catch (Exception e) {
            log.error("AI template creation failed", e);
        }
        
        return template;
    }
    
    @Autowired(required = false)
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
    /**
     * Index video with Azure Video Indexer
     * Returns comprehensive video insights in one call
     */
    private com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureVideoIndexerResult indexVideoWithAzure(String videoUrl) {
        try {
            // Generate signed URL for Azure Video Indexer (2 hours expiration for long videos)
            String signedUrl = videoUrl;
            if (ossStorageService != null && videoUrl.contains("aliyuncs.com")) {
                signedUrl = ossStorageService.generateSignedUrl(videoUrl, 2, java.util.concurrent.TimeUnit.HOURS);
                log.info("Generated signed URL for Azure Video Indexer (expires in 2 hours)");
            }
            
            // Use the enhanced AzureVideoIndexerExtractor to get FULL insights
            AzureVideoIndexerExtractor.AzureVideoIndexerResult azureResult = 
                azureExtractor.extractFullInsights(signedUrl);
            
            // Fallback: If no scenes from Azure, create default scenes
            if (azureResult.scenes.isEmpty() && !azureResult.transcript.isEmpty()) {
                log.warn("No scenes from Azure, creating default 10-second scenes");
                azureResult.scenes = createDefaultScenes(azureResult.transcript);
            }
            
            return azureResult;
            
        } catch (Exception e) {
            log.error("Azure indexing failed", e);
            return null;
        }
    }
    
    /**
     * Create default scenes from subtitles (temporary until full Azure integration)
     */
    private List<com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureScene> createDefaultScenes(List<SubtitleSegment> subtitles) {
        List<com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureScene> scenes = new ArrayList<>();
        
        if (subtitles.isEmpty()) {
            return scenes;
        }
        
        // Group subtitles into ~10 second scenes
        long sceneDuration = 10000; // 10 seconds
        long videoStart = subtitles.get(0).getStartTimeMs();
        long videoEnd = subtitles.get(subtitles.size() - 1).getEndTimeMs();
        
        int sceneId = 1;
        for (long start = videoStart; start < videoEnd; start += sceneDuration) {
            com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureScene scene = new com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureScene();
            scene.id = sceneId++;
            scene.startMs = start;
            scene.endMs = Math.min(start + sceneDuration, videoEnd);
            scenes.add(scene);
        }
        
        return scenes;
    }
    
    /**
     * Create Scene objects from Azure scene boundaries
     */
    private List<Scene> createScenesFromAzure(List<com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureScene> azureScenes) {
        List<Scene> scenes = new ArrayList<>();
        
        for (int i = 0; i < azureScenes.size(); i++) {
            com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureScene azureScene = azureScenes.get(i);
            
            Scene scene = new Scene();
            scene.setSceneNumber(i + 1);
            scene.setSceneSource("ai");
            scene.setStartTimeMs(azureScene.startMs);
            scene.setEndTimeMs(azureScene.endMs);
            scene.setSceneDurationInSeconds((azureScene.endMs - azureScene.startMs) / 1000);
            
            scenes.add(scene);
        }
        
        return scenes;
    }
    
    /**
     * Assign scriptLines to scenes by intelligently choosing between transcript and OCR
     * 
     * Strategy:
     * 1. If transcript confidence < 0.5 (50%), prefer OCR only
     * 2. If transcript confidence >= 0.5, use transcript + OCR combined
     * 3. Always log which source was used for debugging
     * 
     * TODO (OPTIONAL - FUTURE): Add Qwen subtitle cleaning
     * - If OCR has errors or needs punctuation, send to Qwen for correction
     * - Prompt: "Clean and correct these subtitles, maintain timing"
     * - Only implement if current quality is insufficient
     * 
     * Current status: Using raw Azure text (transcript or OCR)
     * - Works well for most videos
     * - OCR-only mode solves low-confidence transcript issues
     */
    private void assignScriptLines(
        List<Scene> scenes,
        List<SubtitleSegment> transcript,
        List<SubtitleSegment> ocr
    ) {
        // Calculate average transcript confidence
        double avgConfidence = transcript.stream()
            .mapToDouble(SubtitleSegment::getConfidence)
            .average()
            .orElse(0.0);
        
        boolean useOcrOnly = avgConfidence < 0.5;
        
        log.info("📝 Subtitle Strategy: Transcript confidence = {}, Using: {}", 
            String.format("%.1f%%", avgConfidence * 100),
            useOcrOnly ? "OCR ONLY (low transcript confidence)" : "TRANSCRIPT + OCR");
        
        // Choose subtitle source
        List<SubtitleSegment> allText = new ArrayList<>();
        if (useOcrOnly) {
            // Low confidence transcript - use OCR only, filtered by continuous position
            List<SubtitleSegment> filteredOcr = filterContinuousSubtitleLine(ocr);
            allText.addAll(filteredOcr);
            log.info("   ⚠️  Transcript confidence too low, using {} filtered OCR segments (from {} total)", 
                filteredOcr.size(), ocr.size());
        } else {
            // Good transcript - combine both
            allText.addAll(transcript);
            allText.addAll(ocr);
            log.info("   ✅ Good transcript confidence, combining transcript + OCR");
        }
        
        // Sort by start time
        allText.sort(Comparator.comparingLong(SubtitleSegment::getStartTimeMs));
        
        // Assign to scenes
        for (Scene scene : scenes) {
            long sceneStart = scene.getStartTimeMs();
            long sceneEnd = scene.getEndTimeMs();
            
            StringBuilder scriptLine = new StringBuilder();
            List<SubtitleSegment> sceneSegments = new ArrayList<>();
            
            for (SubtitleSegment segment : allText) {
                long midpoint = (segment.getStartTimeMs() + segment.getEndTimeMs()) / 2;
                
                if (midpoint >= sceneStart && midpoint < sceneEnd) {
                    // Add to scriptLine (combined text for backward compatibility)
                    if (scriptLine.length() > 0) {
                        scriptLine.append(" ");
                    }
                    scriptLine.append(segment.getText());
                    sceneSegments.add(segment);
                }
            }
            
            scene.setScriptLine(scriptLine.toString().trim());
            scene.setSubtitleSegments(sceneSegments);  // NEW: Store segments with timing!
        }
    }
    
    /**
     * Filter OCR to find the continuous subtitle line at bottom of screen
     * 
     * Strategy: Group all OCR by vertical position, return the largest group
     * This finds subtitle tracks that stay at consistent position (e.g. top ~300)
     */
    private List<SubtitleSegment> filterContinuousSubtitleLine(List<SubtitleSegment> ocr) {
        if (ocr.isEmpty()) return ocr;
        
        // Group by vertical position (±10px tolerance)
        Map<Integer, List<SubtitleSegment>> positionGroups = new HashMap<>();
        List<SubtitleSegment> withoutPosition = new ArrayList<>();
        
        for (SubtitleSegment seg : ocr) {
            if (seg.getTop() == null) {
                withoutPosition.add(seg);
                continue;
            }
            
            // Round to nearest 10px for grouping (e.g. 297-301 → 300)
            int roundedTop = Math.round(seg.getTop() / 10.0f) * 10;
            positionGroups.computeIfAbsent(roundedTop, k -> new ArrayList<>()).add(seg);
        }
        
        // If no position data, return all
        if (positionGroups.isEmpty()) {
            log.info("   📍 No position data, keeping all {} OCR segments", ocr.size());
            return ocr;
        }
        
        // Find the position with most segments
        int totalWithPosition = ocr.size() - withoutPosition.size();
        Map.Entry<Integer, List<SubtitleSegment>> largestGroup = positionGroups.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().size()))
            .orElse(null);
        
        if (largestGroup != null) {
            double ratio = (double) largestGroup.getValue().size() / totalWithPosition;
            
            // Log top 3 positions for debugging
            log.info("   📍 OCR position distribution:");
            positionGroups.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getValue().size()))
                .limit(3)
                .forEach(e -> {
                    String preview = e.getValue().stream()
                        .limit(2)
                        .map(SubtitleSegment::getText)
                        .collect(java.util.stream.Collectors.joining(", "));
                    log.info("      top ~{}: {} segments ({}%) - \"{}...\"", 
                        e.getKey(), e.getValue().size(), 
                        (int)(e.getValue().size() * 100.0 / totalWithPosition),
                        preview.length() > 40 ? preview.substring(0, 40) : preview);
                });
            
            if (ratio > 0.25) {
                // Found dominant position (>25% of segments at same position)
                log.info("   ✅ Using subtitle track at top ~{} ({} segments, {}%)", 
                    largestGroup.getKey(), largestGroup.getValue().size(), (int)(ratio * 100));
                return largestGroup.getValue();
            } else {
                // No clear subtitle track - keep all OCR
                log.info("   ⚠️  No dominant position (max {}%), keeping all {} OCR segments", 
                    (int)(ratio * 100), ocr.size());
                return ocr;
            }
        }
        
        return ocr;
    }
    
    /**
     * Parse overlay objects from vlRawResponse JSON
     * Extracts regions with bounding boxes from the VL response
     */
    private List<Scene.ObjectOverlay> parseOverlayObjectsFromVlResponse(String vlRawResponse) {
        List<Scene.ObjectOverlay> objects = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(vlRawResponse);
            if (root.has("regions") && root.get("regions").isArray()) {
                JsonNode regions = root.get("regions");
                for (JsonNode region : regions) {
                    if (region.has("box") && region.get("box").isArray()) {
                        JsonNode boxNode = region.get("box");
                        if (boxNode.size() == 4) {
                            Scene.ObjectOverlay obj = new Scene.ObjectOverlay();
                            obj.setLabelZh(region.path("labelZh").asText("未知"));
                            obj.setLabelLocalized(region.path("labelZh").asText("未知"));
                            obj.setLabel(region.path("labelZh").asText("unknown"));
                            obj.setConfidence((float) region.path("conf").asDouble(0.0));
                            // Convert from pixel coordinates to normalized 0-1
                            obj.setX((float) boxNode.get(0).asInt() / 1000.0f);
                            obj.setY((float) boxNode.get(1).asInt() / 1000.0f);
                            obj.setWidth((float) boxNode.get(2).asInt() / 1000.0f);
                            obj.setHeight((float) boxNode.get(3).asInt() / 1000.0f);
                            objects.add(obj);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse vlRawResponse: {}", e.getMessage());
        }
        return objects;
    }
    
    /**
     * Analyze each scene with Qwen VL
     * 
     * For each scene:
     * 1. Extract keyframe (from Azure shots or video)
     * 2. Get Azure detected objects for this scene
     * 3. Qwen VL grounding: Ground objects with bounding boxes
     * 4. Qwen VL analysis: Scene understanding + guidance
     */
    private void analyzeScenes(
        List<Scene> scenes,
        String videoUrl,
        com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureVideoIndexerResult azureResult,
        String language
    ) {
        for (Scene scene : scenes) {
            try {
                log.info("Analyzing scene {}/{}", scene.getSceneNumber(), scenes.size());
                
                // Get scene timing
                Duration startTime = Duration.ofMillis(scene.getStartTimeMs());
                Duration endTime = Duration.ofMillis(scene.getEndTimeMs());
                
                // Filter Azure detected objects for this scene's time range
                List<String> azureObjectHints = filterAzureObjectsForScene(
                    azureResult.detectedObjects, 
                    scene.getStartTimeMs(), 
                    scene.getEndTimeMs()
                );
                
                if (!azureObjectHints.isEmpty()) {
                    log.info("🎯 Scene {} has {} Azure object hints: {}", 
                        scene.getSceneNumber(), azureObjectHints.size(), azureObjectHints);
                } else {
                    log.info("⚠️ Scene {} has no Azure object hints", scene.getSceneNumber());
                }
                
                // Analyze with UnifiedSceneAnalysisService
                // This will:
                // - Extract keyframe
                // - Use Azure object hints for targeted Qwen VL grounding
                // - Qwen VL analysis
                String scriptLine = scene.getScriptLine();
                log.info("🎬 Analyzing scene {} - ScriptLine: \"{}\"", 
                    scene.getSceneNumber(),
                    scriptLine != null && !scriptLine.isEmpty() 
                        ? (scriptLine.length() > 80 ? scriptLine.substring(0, 80) + "..." : scriptLine)
                        : "(empty)");
                
                SceneAnalysisResult analysis = sceneAnalysisService.analyzeScene(
                    videoUrl,
                    null, // No provided regions - auto-detect
                    language,
                    startTime,
                    endTime,
                    scriptLine, // Pass scriptLine for context
                    azureObjectHints // Pass Azure detected objects as hints
                );
                
                // Apply analysis results to scene
                scene.setKeyframeUrl(analysis.getKeyframeUrl());
                scene.setOverlayType(analysis.getOverlayType());
                scene.setOverlayObjects(analysis.getOverlayObjects());
                scene.setOverlayPolygons(analysis.getOverlayPolygons());
                scene.setSourceAspect(analysis.getSourceAspect());
                scene.setShortLabelZh(analysis.getShortLabelZh());
                scene.setVlRawResponse(analysis.getVlRawResponse());
                scene.setVlSceneAnalysis(analysis.getVlSceneAnalysis());
                scene.setKeyElements(analysis.getKeyElements());
                
                // IMPORTANT: If overlayObjects is null but vlRawResponse has regions, parse them
                if ((scene.getOverlayObjects() == null || scene.getOverlayObjects().isEmpty()) 
                    && scene.getVlRawResponse() != null) {
                    List<Scene.ObjectOverlay> parsedObjects = parseOverlayObjectsFromVlResponse(scene.getVlRawResponse());
                    if (!parsedObjects.isEmpty()) {
                        scene.setOverlayObjects(parsedObjects);
                        scene.setOverlayType("objects");
                        log.info("✅ Parsed {} overlay objects from vlRawResponse for scene {}", 
                            parsedObjects.size(), scene.getSceneNumber());
                    }
                }
                
                log.info("✅ Scene {} analyzed: overlayType={}, keyElements={}",
                    scene.getSceneNumber(),
                    scene.getOverlayType(),
                    scene.getKeyElements() != null ? scene.getKeyElements().size() : 0);
                
            } catch (Exception e) {
                log.error("Failed to analyze scene {}", scene.getSceneNumber(), e);
            }
        }
    }
    
    /**
     * Filter Azure detected objects for a specific scene time range
     * Returns list of object display names that appear in this scene
     */
    private List<String> filterAzureObjectsForScene(
        List<com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureDetectedObject> detectedObjects,
        long sceneStartMs,
        long sceneEndMs
    ) {
        List<String> hints = new ArrayList<>();
        
        if (detectedObjects == null || detectedObjects.isEmpty()) {
            return hints;
        }
        
        for (com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureDetectedObject obj : detectedObjects) {
            // Check if any instance of this object overlaps with the scene
            boolean overlaps = false;
            for (com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureInstance inst : obj.instances) {
                // Check if instance overlaps with scene
                if (inst.startMs < sceneEndMs && inst.endMs > sceneStartMs) {
                    overlaps = true;
                    break;
                }
            }
            
            if (overlaps && obj.displayName != null && !obj.displayName.isEmpty()) {
                hints.add(obj.displayName);
            }
        }
        
        // Limit to top 5 objects to avoid overwhelming Qwen VL
        if (hints.size() > 5) {
            log.info("Limiting Azure hints from {} to 5 objects", hints.size());
            hints = hints.subList(0, 5);
        }
        
        return hints;
    }
    
    /**
     * Generate template metadata using Qwen reasoning
     * 
     * Generates (ALL from Qwen in one call):
     * - Template-level: videoPurpose, tone, lighting, backgroundMusic
     * - Per-scene guidance: camera, movement, audio, background
     */
    private void generateMetadata(ManualTemplate template, List<Scene> scenes, String userDescription, Video video) {
        log.info("[METADATA] Generating template metadata with Qwen");
        
        try {
            // Build payload for Qwen
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> tpl = new HashMap<>();
            tpl.put("videoTitle", video.getTitle());
            tpl.put("language", "zh-CN");
            tpl.put("totalDurationSeconds", calculateTotalDuration(scenes));
            
            // Add user description if provided (helps Qwen identify purpose)
            if (userDescription != null && !userDescription.isBlank()) {
                tpl.put("userDescription", userDescription);
            }
            
            // Combine all scriptLines for AI context
            String combinedScriptLines = scenes.stream()
                .map(Scene::getScriptLine)
                .filter(sl -> sl != null && !sl.isEmpty())
                .collect(java.util.stream.Collectors.joining(" | "));
            
            if (!combinedScriptLines.isEmpty()) {
                tpl.put("combinedScriptLines", combinedScriptLines);
                log.info("[METADATA] Combined scriptLines for AI: \"{}\"", 
                    combinedScriptLines.length() > 100 ? combinedScriptLines.substring(0, 100) + "..." : combinedScriptLines);
            }
            
            payload.put("template", tpl);
            
            // Build scene data
            List<Map<String, Object>> sceneArr = new ArrayList<>();
            for (Scene s : scenes) {
                Map<String, Object> so = new HashMap<>();
                so.put("sceneNumber", s.getSceneNumber());
                so.put("durationSeconds", s.getSceneDurationInSeconds());
                so.put("startMs", s.getStartTimeMs());
                so.put("endMs", s.getEndTimeMs());
                so.put("keyframeUrl", s.getKeyframeUrl());
                so.put("scriptLine", s.getScriptLine());
                
                // Collect detected object labels
                List<String> labels = new ArrayList<>();
                if (s.getOverlayPolygons() != null) {
                    for (var p : s.getOverlayPolygons()) {
                        if (p.getLabelZh() != null && !p.getLabelZh().isEmpty()) {
                            labels.add(p.getLabelZh());
                        }
                    }
                }
                if (s.getOverlayObjects() != null) {
                    for (var o : s.getOverlayObjects()) {
                        if (o.getLabelZh() != null && !o.getLabelZh().isEmpty()) {
                            labels.add(o.getLabelZh());
                        }
                    }
                }
                if (labels.size() > 5) labels = labels.subList(0, 5);
                so.put("detectedObjects", labels);
                
                // Add VL scene analysis
                if (s.getVlSceneAnalysis() != null && !s.getVlSceneAnalysis().isEmpty()) {
                    so.put("sceneAnalysis", s.getVlSceneAnalysis());
                }
                
                sceneArr.add(so);
            }
            payload.put("scenes", sceneArr);
            
            // Call Qwen for guidance generation
            log.info("[METADATA] Calling objectLabelService.generateTemplateGuidance");
            Map<String, Object> result = objectLabelService.generateTemplateGuidance(payload);
            
            if (result == null || result.isEmpty()) {
                log.warn("[METADATA] AI guidance unavailable, using basic metadata");
                setBasicMetadata(template, scenes);
                return;
            }
            
            // Apply template metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> t = (Map<String, Object>) result.get("template");
            if (t != null) {
                Object vp = t.get("videoPurpose");
                if (vp instanceof String s) {
                    template.setVideoPurpose(trim40(s));
                    log.info("[METADATA] Set videoPurpose: {}", s);
                }
                Object tone = t.get("tone");
                if (tone instanceof String s) {
                    template.setTone(trim40(s));
                    log.info("[METADATA] Set tone: {}", s);
                }
                Object light = t.get("lightingRequirements");
                if (light instanceof String s) {
                    template.setLightingRequirements(trim60(s));
                    log.info("[METADATA] Set lightingRequirements: {}", s);
                }
                Object bgm = t.get("backgroundMusic");
                if (bgm instanceof String s) {
                    template.setBackgroundMusic(trim40(s));
                    log.info("[METADATA] Set backgroundMusic: {}", s);
                }
            }
            
            // Apply per-scene guidance
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rs = (List<Map<String, Object>>) result.get("scenes");
            if (rs != null) {
                Map<Integer, Map<String, Object>> byNum = new HashMap<>();
                for (Map<String, Object> one : rs) {
                    Object n = one.get("sceneNumber");
                    if (n instanceof Number num) byNum.put(num.intValue(), one);
                }
                for (Scene s : scenes) {
                    Map<String, Object> one = byNum.get(s.getSceneNumber());
                    if (one == null) continue;
                    
                    Object person = one.get("presenceOfPerson");
                    if (person instanceof Boolean b) s.setPresenceOfPerson(b);
                    Object move = one.get("movementInstructions");
                    if (move instanceof String v) s.setMovementInstructions(trim60(v));
                    Object bg = one.get("backgroundInstructions");
                    if (bg instanceof String v) s.setBackgroundInstructions(trim60(v));
                    Object cam = one.get("specificCameraInstructions");
                    if (cam instanceof String v) s.setSpecificCameraInstructions(trim60(v));
                    Object audio = one.get("audioNotes");
                    if (audio instanceof String v) s.setAudioNotes(trim60(v));
                }
            }
            
            // Set template title and description
            String today = java.time.LocalDate.now().toString();
            String videoPurpose = template.getVideoPurpose() != null ? template.getVideoPurpose() : "通用视频";
            template.setTemplateTitle("AI 模版 - " + videoPurpose + " " + today);
            template.setTemplateDescription("Automatically generated template for " + videoPurpose);
            
            // Calculate total video length from scenes
            int totalSeconds = calculateTotalDuration(scenes);
            template.setTotalVideoLength(totalSeconds);
            log.info("[METADATA] Set totalVideoLength: {} seconds", totalSeconds);
            
            // Derive device orientation from first scene
            String aspectRatio = deriveDeviceOrientationFromFirstScene(scenes);
            if (aspectRatio != null) {
                for (Scene s : scenes) {
                    s.setDeviceOrientation(aspectRatio);
                }
                template.setVideoFormat("1080p " + aspectRatio);
                log.info("[METADATA] Set video format: 1080p {}", aspectRatio);
            } else {
                template.setVideoFormat("1080p 16:9");
            }
            
            log.info("[METADATA] Metadata generation complete");
            
        } catch (Exception e) {
            log.error("[METADATA] AI guidance generation failed: {}", e.getMessage(), e);
            setBasicMetadata(template, scenes);
        }
    }
    
    /**
     * Set basic metadata when AI guidance is unavailable
     */
    private void setBasicMetadata(ManualTemplate template, List<Scene> scenes) {
        String today = java.time.LocalDate.now().toString();
        template.setTemplateTitle("AI 模版 " + today);
        template.setTemplateDescription("Automatically generated template");
        
        // Generate per-scene guidance from VL analysis
        for (Scene scene : scenes) {
            scene.setSceneTitle("Scene " + scene.getSceneNumber());
            scene.setSceneDescription(scene.getScriptLine());
            
            if (scene.getVlSceneAnalysis() != null) {
                scene.setBackgroundInstructions("参考场景分析: " + scene.getVlSceneAnalysis());
            }
            
            if (scene.getKeyElements() != null && !scene.getKeyElements().isEmpty()) {
                scene.setSpecificCameraInstructions("重点拍摄: " + String.join(", ", scene.getKeyElements()));
            }
        }
        
        // Derive device orientation
        String aspectRatio = deriveDeviceOrientationFromFirstScene(scenes);
        if (aspectRatio != null) {
            for (Scene s : scenes) {
                s.setDeviceOrientation(aspectRatio);
            }
            template.setVideoFormat("1080p " + aspectRatio);
        } else {
            template.setVideoFormat("1080p 16:9");
        }
    }
    
    /**
     * Utility methods for trimming AI-generated text to field length limits
     */
    private String trim40(String s) {
        return s == null ? null : (s.length() > 40 ? s.substring(0, 40) : s);
    }
    
    private String trim60(String s) {
        return s == null ? null : (s.length() > 60 ? s.substring(0, 60) : s);
    }
    
    /**
     * Calculate total duration from scenes
     */
    private int calculateTotalDuration(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return 0;
        }
        
        long maxEndMs = 0;
        for (Scene scene : scenes) {
            if (scene.getEndTimeMs() != null && scene.getEndTimeMs() > maxEndMs) {
                maxEndMs = scene.getEndTimeMs();
            }
        }
        
        return (int) (maxEndMs / 1000);
    }
    
    /**
     * Derive device orientation (aspect ratio) from first scene's keyframe dimensions
     * Returns "9:16" for portrait or "16:9" for landscape
     */
    private String deriveDeviceOrientationFromFirstScene(List<Scene> scenes) {
        try {
            for (Scene s : scenes) {
                if (s.getKeyframeUrl() == null || s.getKeyframeUrl().isBlank()) continue;
                BufferedImage img = ImageIO.read(new URL(s.getKeyframeUrl()));
                if (img == null) continue;
                int w = img.getWidth();
                int h = img.getHeight();
                boolean portrait = h >= w;
                return portrait ? "9:16" : "16:9";
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    /**
     * Log scriptLines for debugging
     */
    private void logScriptLines(List<Scene> scenes) {
        log.info("✅ ScriptLines assigned:");
        for (Scene scene : scenes) {
            String scriptLine = scene.getScriptLine();
            if (scriptLine != null && !scriptLine.isEmpty()) {
                String preview = scriptLine.length() > 60 ? 
                    scriptLine.substring(0, 60) + "..." : scriptLine;
                log.info("  Scene {}: \"{}\"", scene.getSceneNumber(), preview);
            } else {
                log.info("  Scene {}: (empty)", scene.getSceneNumber());
            }
        }
    }
    
    // ========== Reusable Methods ==========
    
    /**
     * Process a single scene video with Azure Video Indexer
     * REUSABLE by both AI and Manual template creation
     * 
     * @param scene Scene object to populate
     * @param videoUrl Video URL to analyze
     * @param azureResult Azure Video Indexer result for this video
     * @param language Language for analysis
     */
    public void processSingleSceneWithAzure(
        Scene scene,
        String videoUrl,
        com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureVideoIndexerResult azureResult,
        String language
    ) {
        log.info("[PROCESS-SCENE] Processing scene {} with Azure data", scene.getSceneNumber());
        
        // Step 1: Assign scriptLine (combine transcript + filtered OCR)
        List<Scene> singleSceneList = Arrays.asList(scene);
        assignScriptLines(singleSceneList, azureResult.transcript, azureResult.ocr);
        log.info("[PROCESS-SCENE] ScriptLine assigned: \"{}\"", 
            scene.getScriptLine() != null ? 
                (scene.getScriptLine().length() > 80 ? scene.getScriptLine().substring(0, 80) + "..." : scene.getScriptLine()) 
                : "(empty)");
        
        // Step 2: Get Azure object hints (all objects since entire video = 1 scene)
        List<String> azureObjectHints = new ArrayList<>();
        for (com.example.demo.ai.subtitle.AzureVideoIndexerExtractor.AzureDetectedObject obj : azureResult.detectedObjects) {
            if (obj.displayName != null && !obj.displayName.isEmpty()) {
                azureObjectHints.add(obj.displayName);
            }
        }
        
        if (!azureObjectHints.isEmpty()) {
            log.info("[PROCESS-SCENE] Azure object hints: {}", azureObjectHints);
        }
        
        // Step 3: Analyze scene with Qwen VL (using Azure hints)
        try {
            SceneAnalysisResult analysis = sceneAnalysisService.analyzeScene(
                videoUrl,
                null, // No provided regions - auto-detect
                language,
                Duration.ofMillis(scene.getStartTimeMs()),
                Duration.ofMillis(scene.getEndTimeMs()),
                scene.getScriptLine(), // Pass scriptLine for context
                azureObjectHints // Pass Azure detected objects as hints
            );
            
            // Apply analysis results to scene
            scene.setKeyframeUrl(analysis.getKeyframeUrl());
            scene.setOverlayType(analysis.getOverlayType());
            scene.setOverlayObjects(analysis.getOverlayObjects());
            scene.setOverlayPolygons(analysis.getOverlayPolygons());
            scene.setSourceAspect(analysis.getSourceAspect());
            scene.setShortLabelZh(analysis.getShortLabelZh());
            scene.setVlRawResponse(analysis.getVlRawResponse());
            scene.setVlSceneAnalysis(analysis.getVlSceneAnalysis());
            scene.setKeyElements(analysis.getKeyElements());
            
            log.info("[PROCESS-SCENE] ✅ Scene {} analyzed: overlayType={}, keyElements={}",
                scene.getSceneNumber(),
                scene.getOverlayType(),
                scene.getKeyElements() != null ? scene.getKeyElements().size() : 0);
                
        } catch (Exception e) {
            log.error("[PROCESS-SCENE] Failed to analyze scene {}", scene.getSceneNumber(), e);
        }
    }
}
