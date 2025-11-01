package com.example.demo.ai.subtitle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Extracts subtitles using Azure Video Indexer
 * 
 * Azure Video Indexer provides:
 * - Transcript extraction with word-level timing
 * - OCR for on-screen text
 * - Scene detection
 * - Much better accuracy than other services
 * 
 * API Documentation: https://learn.microsoft.com/en-us/rest/api/videoindexer/
 * 
 * Workflow:
 * 1. Get access token
 * 2. Upload video or provide URL
 * 3. Poll for indexing completion
 * 4. Get transcript with timing
 */
@Service
public class AzureVideoIndexerExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(AzureVideoIndexerExtractor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    
    @Value("${AZURE_VIDEO_INDEXER_ACCOUNT_ID:}")
    private String accountId;  // GUID from Azure Video Indexer resource Properties
    
    @Value("${AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY:}")
    private String subscriptionKey;  // Primary/Secondary key from VI Developer Portal
    
    @Value("${AZURE_VIDEO_INDEXER_LOCATION:eastasia}")
    private String location;  // Region code: eastasia, southeastasia, eastus, westus2, etc.
    
    private static final String API_BASE = "https://api.videoindexer.ai";
    
    /**
     * Extract subtitles from video using Azure Video Indexer
     * 
     * @param videoUrl Public URL to video file
     * @return List of subtitle segments with timing
     */
    public List<SubtitleSegment> extract(String videoUrl) {
        log.info("Starting Azure Video Indexer subtitle extraction for video: {}", videoUrl);
        
        try {
            // Check credentials
            if (subscriptionKey == null || subscriptionKey.isEmpty()) {
                throw new IllegalStateException("Azure Video Indexer subscription key not configured");
            }
            
            if (accountId == null || accountId.isEmpty()) {
                throw new IllegalStateException("Azure Video Indexer account ID not configured");
            }
            
            // Step 1: Get access token
            String accessToken = getAccessToken();
            log.info("✅ Got access token");
            
            // Step 2: Upload video and start indexing
            String videoId = uploadVideo(videoUrl, accessToken);
            log.info("✅ Video uploaded, ID: {}", videoId);
            
            // Step 3: Poll for indexing completion
            waitForIndexing(videoId, accessToken);
            log.info("✅ Indexing completed");
            
            // Step 4: Get transcript
            List<SubtitleSegment> subtitles = getTranscript(videoId, accessToken);
            log.info("✅ Extracted {} subtitle segments", subtitles.size());
            
            return subtitles;
            
        } catch (Exception e) {
            log.error("Azure Video Indexer extraction failed", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Extract FULL insights from video using Azure Video Indexer
     * Returns transcript, OCR, scenes, shots, keyframes, labels, and detected objects
     * 
     * @param videoUrl Public URL to video file
     * @return Complete Azure Video Indexer result with all insights
     */
    public AzureVideoIndexerResult extractFullInsights(String videoUrl) {
        log.info("Starting Azure Video Indexer FULL extraction for video: {}", videoUrl);
        
        AzureVideoIndexerResult result = new AzureVideoIndexerResult();
        
        try {
            // Check credentials
            if (subscriptionKey == null || subscriptionKey.isEmpty()) {
                throw new IllegalStateException("Azure Video Indexer subscription key not configured");
            }
            
            if (accountId == null || accountId.isEmpty()) {
                throw new IllegalStateException("Azure Video Indexer account ID not configured");
            }
            
            // Step 1: Get access token
            String accessToken = getAccessToken();
            log.info("✅ Got access token");
            
            // Step 2: Upload video and start indexing
            String videoId = uploadVideo(videoUrl, accessToken);
            log.info("✅ Video uploaded, ID: {}", videoId);
            
            // Step 3: Poll for indexing completion
            waitForIndexing(videoId, accessToken);
            log.info("✅ Indexing completed");
            
            // Step 4: Get full insights JSON
            JsonNode insightsJson = getFullInsights(videoId, accessToken);
            
            // Step 5: Parse all insights
            result.transcript = parseTranscript(insightsJson);
            result.ocr = parseOCR(insightsJson);
            result.scenes = parseScenes(insightsJson);
            result.shots = parseShots(insightsJson);
            result.labels = parseLabels(insightsJson);
            result.detectedObjects = parseDetectedObjects(insightsJson);
            
            log.info("✅ Full extraction complete:");
            log.info("  - Transcript: {} segments", result.transcript.size());
            log.info("  - OCR: {} segments", result.ocr.size());
            log.info("  - Scenes: {}", result.scenes.size());
            log.info("  - Shots: {}", result.shots.size());
            log.info("  - Labels: {}", result.labels.size());
            log.info("  - Detected Objects: {}", result.detectedObjects.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Azure Video Indexer full extraction failed", e);
            return result; // Return partial result
        }
    }
    
    /**
     * Get full insights JSON from indexed video
     */
    private JsonNode getFullInsights(String videoId, String accessToken) throws Exception {
        String url = String.format("%s/%s/Accounts/%s/Videos/%s/Index?accessToken=%s",
            API_BASE, location, accountId, videoId, accessToken);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get insights: " + response.body());
        }
        
        return mapper.readTree(response.body());
    }
    
    /**
     * Get access token for Video Indexer API
     * 
     * Uses classic gateway authentication with Primary/Secondary key
     * NOTE: "Auth" must be CAPITALIZED (not "auth")
     */
    private String getAccessToken() throws Exception {
        // IMPORTANT: Auth is CAPITALIZED for classic gateway
        String url = String.format("%s/Auth/%s/Accounts/%s/AccessToken?allowEdit=true",
            API_BASE, location, accountId);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Ocp-Apim-Subscription-Key", subscriptionKey)  // Primary key from VI Developer Portal
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get access token: " + response.body());
        }
        
        // Response is just the token string (with quotes)
        return response.body().replace("\"", "");
    }
    
    /**
     * Upload video and start indexing
     */
    private String uploadVideo(String videoUrl, String accessToken) throws Exception {
        String videoName = "video_" + System.currentTimeMillis();
        
        String url = String.format("%s/%s/Accounts/%s/Videos?accessToken=%s&name=%s&videoUrl=%s",
            API_BASE, location, accountId, accessToken, videoName, 
            java.net.URLEncoder.encode(videoUrl, "UTF-8"));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to upload video: " + response.body());
        }
        
        JsonNode json = mapper.readTree(response.body());
        return json.get("id").asText();
    }
    
    /**
     * Poll for indexing completion
     */
    private void waitForIndexing(String videoId, String accessToken) throws Exception {
        int maxAttempts = 120; // 10 minutes max
        int pollInterval = 5000; // 5 seconds
        
        log.info("Polling for indexing completion (videoId: {})", videoId);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String url = String.format("%s/%s/Accounts/%s/Videos/%s/Index?accessToken=%s",
                API_BASE, location, accountId, videoId, accessToken);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to get video status: " + response.body());
            }
            
            JsonNode json = mapper.readTree(response.body());
            String state = json.get("state").asText();
            
            log.info("Indexing status (attempt {}/{}): {}", attempt, maxAttempts, state);
            
            if ("Processed".equals(state)) {
                return; // Done!
            }
            
            if ("Failed".equals(state)) {
                String errorMessage = json.has("failureMessage") ? 
                    json.get("failureMessage").asText() : "Unknown error";
                throw new RuntimeException("Indexing failed: " + errorMessage);
            }
            
            Thread.sleep(pollInterval);
        }
        
        throw new RuntimeException("Indexing timed out after " + maxAttempts + " attempts");
    }
    
    /**
     * Get transcript from indexed video
     */
    private List<SubtitleSegment> getTranscript(String videoId, String accessToken) throws Exception {
        String url = String.format("%s/%s/Accounts/%s/Videos/%s/Index?accessToken=%s",
            API_BASE, location, accountId, videoId, accessToken);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get transcript: " + response.body());
        }
        
        JsonNode json = mapper.readTree(response.body());
        return parseTranscript(json);
    }
    
    /**
     * Parse transcript from Video Indexer response
     */
    private List<SubtitleSegment> parseTranscript(JsonNode json) {
        List<SubtitleSegment> subtitles = new ArrayList<>();
        
        try {
            // Navigate to transcript
            JsonNode videos = json.get("videos");
            if (videos == null || !videos.isArray() || videos.size() == 0) {
                log.warn("No videos in response");
                return subtitles;
            }
            
            JsonNode video = videos.get(0);
            JsonNode insights = video.get("insights");
            if (insights == null) {
                log.warn("No insights in video");
                return subtitles;
            }
            
            JsonNode transcript = insights.get("transcript");
            if (transcript == null || !transcript.isArray()) {
                log.warn("No transcript in insights");
                return subtitles;
            }
            
            // Parse transcript lines
            for (JsonNode line : transcript) {
                String text = line.get("text").asText();
                double confidence = line.get("confidence").asDouble();
                
                // Get timing from instances
                JsonNode instances = line.get("instances");
                if (instances != null && instances.isArray() && instances.size() > 0) {
                    JsonNode instance = instances.get(0);
                    
                    // Parse time format: "0:00:01.5" -> milliseconds
                    String startStr = instance.get("start").asText();
                    String endStr = instance.get("end").asText();
                    
                    long startMs = parseTime(startStr);
                    long endMs = parseTime(endStr);
                    
                    subtitles.add(new SubtitleSegment(startMs, endMs, text, confidence));
                }
            }
            
            log.info("Parsed {} transcript lines", subtitles.size());
            
        } catch (Exception e) {
            log.error("Failed to parse transcript", e);
        }
        
        return subtitles;
    }
    
    /**
     * Parse OCR from Video Indexer response
     */
    private List<SubtitleSegment> parseOCR(JsonNode json) {
        List<SubtitleSegment> ocrSegments = new ArrayList<>();
        
        try {
            JsonNode videos = json.get("videos");
            if (videos == null || !videos.isArray() || videos.size() == 0) return ocrSegments;
            
            JsonNode video = videos.get(0);
            JsonNode insights = video.get("insights");
            if (insights == null) return ocrSegments;
            
            JsonNode ocr = insights.get("ocr");
            if (ocr == null || !ocr.isArray()) {
                log.info("No OCR in insights");
                return ocrSegments;
            }
            
            // Parse OCR lines
            for (JsonNode line : ocr) {
                String text = line.get("text").asText();
                double confidence = line.get("confidence").asDouble();
                
                // Get timing from instances
                JsonNode instances = line.get("instances");
                if (instances != null && instances.isArray() && instances.size() > 0) {
                    JsonNode instance = instances.get(0);
                    
                    String startStr = instance.get("start").asText();
                    String endStr = instance.get("end").asText();
                    
                    long startMs = parseTime(startStr);
                    long endMs = parseTime(endStr);
                    
                    ocrSegments.add(new SubtitleSegment(startMs, endMs, text, confidence));
                }
            }
            
            log.info("Parsed {} OCR segments", ocrSegments.size());
            
        } catch (Exception e) {
            log.error("Failed to parse OCR", e);
        }
        
        return ocrSegments;
    }
    
    /**
     * Parse scenes from Video Indexer response
     */
    private List<AzureScene> parseScenes(JsonNode json) {
        List<AzureScene> scenes = new ArrayList<>();
        
        try {
            JsonNode videos = json.get("videos");
            if (videos == null || !videos.isArray() || videos.size() == 0) return scenes;
            
            JsonNode video = videos.get(0);
            JsonNode insights = video.get("insights");
            if (insights == null) return scenes;
            
            JsonNode scenesNode = insights.get("scenes");
            if (scenesNode == null || !scenesNode.isArray()) {
                log.info("No scenes in insights");
                return scenes;
            }
            
            // Parse scenes
            for (JsonNode sceneNode : scenesNode) {
                int id = sceneNode.get("id").asInt();
                
                JsonNode instances = sceneNode.get("instances");
                if (instances != null && instances.isArray() && instances.size() > 0) {
                    JsonNode instance = instances.get(0);
                    
                    String startStr = instance.get("start").asText();
                    String endStr = instance.get("end").asText();
                    
                    long startMs = parseTime(startStr);
                    long endMs = parseTime(endStr);
                    
                    AzureScene scene = new AzureScene();
                    scene.id = id;
                    scene.startMs = startMs;
                    scene.endMs = endMs;
                    scenes.add(scene);
                }
            }
            
            log.info("Parsed {} scenes", scenes.size());
            
        } catch (Exception e) {
            log.error("Failed to parse scenes", e);
        }
        
        return scenes;
    }
    
    /**
     * Parse shots from Video Indexer response
     */
    private List<AzureShot> parseShots(JsonNode json) {
        List<AzureShot> shots = new ArrayList<>();
        
        try {
            JsonNode videos = json.get("videos");
            if (videos == null || !videos.isArray() || videos.size() == 0) return shots;
            
            JsonNode video = videos.get(0);
            JsonNode insights = video.get("insights");
            if (insights == null) return shots;
            
            JsonNode shotsNode = insights.get("shots");
            if (shotsNode == null || !shotsNode.isArray()) {
                log.info("No shots in insights");
                return shots;
            }
            
            // Parse shots
            for (JsonNode shotNode : shotsNode) {
                int id = shotNode.get("id").asInt();
                
                AzureShot shot = new AzureShot();
                shot.id = id;
                shot.keyframes = new ArrayList<>();
                
                // Parse keyframes
                JsonNode keyFramesNode = shotNode.get("keyFrames");
                if (keyFramesNode != null && keyFramesNode.isArray()) {
                    for (JsonNode kfNode : keyFramesNode) {
                        int kfId = kfNode.get("id").asInt();
                        
                        JsonNode instances = kfNode.get("instances");
                        if (instances != null && instances.isArray() && instances.size() > 0) {
                            JsonNode instance = instances.get(0);
                            
                            String thumbnailId = instance.get("thumbnailId").asText();
                            String startStr = instance.get("start").asText();
                            
                            long startMs = parseTime(startStr);
                            
                            AzureKeyframe keyframe = new AzureKeyframe();
                            keyframe.id = kfId;
                            keyframe.thumbnailId = thumbnailId;
                            keyframe.startMs = startMs;
                            shot.keyframes.add(keyframe);
                        }
                    }
                }
                
                // Get shot timing from instances
                JsonNode instances = shotNode.get("instances");
                if (instances != null && instances.isArray() && instances.size() > 0) {
                    JsonNode instance = instances.get(0);
                    
                    String startStr = instance.get("start").asText();
                    String endStr = instance.get("end").asText();
                    
                    shot.startMs = parseTime(startStr);
                    shot.endMs = parseTime(endStr);
                }
                
                shots.add(shot);
            }
            
            log.info("Parsed {} shots with {} total keyframes", shots.size(), 
                shots.stream().mapToInt(s -> s.keyframes.size()).sum());
            
        } catch (Exception e) {
            log.error("Failed to parse shots", e);
        }
        
        return shots;
    }
    
    /**
     * Parse labels from Video Indexer response
     */
    private List<AzureLabel> parseLabels(JsonNode json) {
        List<AzureLabel> labels = new ArrayList<>();
        
        try {
            JsonNode videos = json.get("videos");
            if (videos == null || !videos.isArray() || videos.size() == 0) return labels;
            
            JsonNode video = videos.get(0);
            JsonNode insights = video.get("insights");
            if (insights == null) return labels;
            
            JsonNode labelsNode = insights.get("labels");
            if (labelsNode == null || !labelsNode.isArray()) {
                log.info("No labels in insights");
                return labels;
            }
            
            // Parse labels
            for (JsonNode labelNode : labelsNode) {
                int id = labelNode.get("id").asInt();
                String name = labelNode.get("name").asText();
                
                AzureLabel label = new AzureLabel();
                label.id = id;
                label.name = name;
                label.instances = new ArrayList<>();
                
                // Parse instances
                JsonNode instances = labelNode.get("instances");
                if (instances != null && instances.isArray()) {
                    for (JsonNode instance : instances) {
                        double confidence = instance.has("confidence") ? instance.get("confidence").asDouble() : 0.0;
                        String startStr = instance.get("start").asText();
                        String endStr = instance.get("end").asText();
                        
                        long startMs = parseTime(startStr);
                        long endMs = parseTime(endStr);
                        
                        AzureInstance inst = new AzureInstance();
                        inst.confidence = confidence;
                        inst.startMs = startMs;
                        inst.endMs = endMs;
                        label.instances.add(inst);
                    }
                }
                
                labels.add(label);
            }
            
            log.info("Parsed {} labels", labels.size());
            
        } catch (Exception e) {
            log.error("Failed to parse labels", e);
        }
        
        return labels;
    }
    
    /**
     * Parse detected objects from Video Indexer response
     */
    private List<AzureDetectedObject> parseDetectedObjects(JsonNode json) {
        List<AzureDetectedObject> objects = new ArrayList<>();
        
        try {
            JsonNode videos = json.get("videos");
            if (videos == null || !videos.isArray() || videos.size() == 0) return objects;
            
            JsonNode video = videos.get(0);
            JsonNode insights = video.get("insights");
            if (insights == null) return objects;
            
            JsonNode objectsNode = insights.get("detectedObjects");
            if (objectsNode == null || !objectsNode.isArray()) {
                log.info("No detected objects in insights");
                return objects;
            }
            
            // Parse detected objects
            for (JsonNode objNode : objectsNode) {
                int id = objNode.get("id").asInt();
                String type = objNode.has("type") ? objNode.get("type").asText() : "";
                String displayName = objNode.has("displayName") ? objNode.get("displayName").asText() : "";
                String thumbnailId = objNode.has("thumbnailId") ? objNode.get("thumbnailId").asText() : "";
                
                AzureDetectedObject obj = new AzureDetectedObject();
                obj.id = id;
                obj.type = type;
                obj.displayName = displayName;
                obj.thumbnailId = thumbnailId;
                obj.instances = new ArrayList<>();
                
                // Parse instances
                JsonNode instances = objNode.get("instances");
                if (instances != null && instances.isArray()) {
                    for (JsonNode instance : instances) {
                        double confidence = instance.has("confidence") ? instance.get("confidence").asDouble() : 0.0;
                        String startStr = instance.get("start").asText();
                        String endStr = instance.get("end").asText();
                        
                        long startMs = parseTime(startStr);
                        long endMs = parseTime(endStr);
                        
                        AzureInstance inst = new AzureInstance();
                        inst.confidence = confidence;
                        inst.startMs = startMs;
                        inst.endMs = endMs;
                        obj.instances.add(inst);
                    }
                }
                
                objects.add(obj);
            }
            
            log.info("Parsed {} detected objects", objects.size());
            
        } catch (Exception e) {
            log.error("Failed to parse detected objects", e);
        }
        
        return objects;
    }
    
    /**
     * Parse Azure Video Indexer time format to milliseconds
     * Format: "0:00:01.5" or "0:00:01.567"
     */
    private long parseTime(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            
            return (long) ((hours * 3600 + minutes * 60 + seconds) * 1000);
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr);
            return 0;
        }
    }
    
    /**
     * Group Azure subtitles by scene timing
     * Returns subtitle segments for each scene (with timing for KTV display and video compilation)
     * 
     * @param subtitles List of subtitle segments from Azure (transcript + OCR)
     * @param scenes List of scene data with timing
     * @return Map of scene number to List of SubtitleSegment
     */
    public Map<Integer, List<SubtitleSegment>> groupSubtitlesByScenes(
        List<SubtitleSegment> subtitles, 
        List<Map<String, Object>> scenes
    ) {
        Map<Integer, List<SubtitleSegment>> result = new HashMap<>();
        
        if (subtitles == null || subtitles.isEmpty() || scenes == null || scenes.isEmpty()) {
            log.warn("Empty subtitles or scenes, returning empty map");
            return result;
        }
        
        log.info("Grouping {} Azure subtitles into {} scenes", subtitles.size(), scenes.size());
        
        for (Map<String, Object> scene : scenes) {
            int sceneNumber = ((Number) scene.get("sceneNumber")).intValue();
            long sceneStart = ((Number) scene.get("startMs")).longValue();
            long sceneEnd = ((Number) scene.get("endMs")).longValue();
            
            List<SubtitleSegment> sceneSegments = new ArrayList<>();
            
            // Find subtitles that overlap with this scene
            for (SubtitleSegment subtitle : subtitles) {
                long subtitleMid = (subtitle.getStartTimeMs() + subtitle.getEndTimeMs()) / 2;
                
                if (subtitleMid >= sceneStart && subtitleMid < sceneEnd) {
                    sceneSegments.add(subtitle);
                }
            }
            
            result.put(sceneNumber, sceneSegments);
            
            if (!sceneSegments.isEmpty()) {
                String preview = sceneSegments.stream()
                    .map(SubtitleSegment::getText)
                    .collect(java.util.stream.Collectors.joining(" "));
                log.info("Scene {}: {} segments - \"{}\"", 
                    sceneNumber,
                    sceneSegments.size(),
                    preview.length() > 60 ? preview.substring(0, 60) + "..." : preview);
            }
        }
        
        return result;
    }
    
    // ========== Result Classes ==========
    
    /**
     * Complete Azure Video Indexer result with all insights
     */
    public static class AzureVideoIndexerResult {
        public List<SubtitleSegment> transcript = new ArrayList<>();
        public List<SubtitleSegment> ocr = new ArrayList<>();
        public List<AzureScene> scenes = new ArrayList<>();
        public List<AzureShot> shots = new ArrayList<>();
        public List<AzureLabel> labels = new ArrayList<>();
        public List<AzureDetectedObject> detectedObjects = new ArrayList<>();
    }
    
    public static class AzureScene {
        public int id;
        public long startMs;
        public long endMs;
    }
    
    public static class AzureShot {
        public int id;
        public long startMs;
        public long endMs;
        public List<AzureKeyframe> keyframes = new ArrayList<>();
    }
    
    public static class AzureKeyframe {
        public int id;
        public String thumbnailId;
        public long startMs;
    }
    
    public static class AzureLabel {
        public int id;
        public String name;
        public double confidence;
        public List<AzureInstance> instances = new ArrayList<>();
    }
    
    public static class AzureDetectedObject {
        public int id;
        public String type;
        public String displayName;
        public String thumbnailId;
        public List<AzureInstance> instances = new ArrayList<>();
    }
    
    public static class AzureInstance {
        public double confidence;
        public long startMs;
        public long endMs;
    }
}
