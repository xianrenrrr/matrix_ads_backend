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
    private String accountId;
    
    @Value("${AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY:}")
    private String subscriptionKey;
    
    @Value("${AZURE_VIDEO_INDEXER_LOCATION:trial}")
    private String location; // e.g., "trial", "eastus", "westus2"
    
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
     * Get access token for Video Indexer API
     */
    private String getAccessToken() throws Exception {
        String url = String.format("%s/auth/%s/Accounts/%s/AccessToken?allowEdit=true",
            API_BASE, location, accountId);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Ocp-Apim-Subscription-Key", subscriptionKey)
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
     */
    public Map<Integer, String> groupByScenes(List<SubtitleSegment> subtitles, List<Map<String, Object>> scenes) {
        Map<Integer, String> scriptLinesByScene = new HashMap<>();
        
        if (subtitles == null || subtitles.isEmpty() || scenes == null || scenes.isEmpty()) {
            log.warn("Empty subtitles or scenes, returning empty map");
            return scriptLinesByScene;
        }
        
        log.info("Grouping {} Azure subtitles into {} scenes", subtitles.size(), scenes.size());
        
        for (Map<String, Object> scene : scenes) {
            int sceneNumber = ((Number) scene.get("sceneNumber")).intValue();
            long sceneStart = ((Number) scene.get("startMs")).longValue();
            long sceneEnd = ((Number) scene.get("endMs")).longValue();
            
            StringBuilder sceneText = new StringBuilder();
            
            // Find subtitles that overlap with this scene
            for (SubtitleSegment subtitle : subtitles) {
                long subtitleMid = (subtitle.getStartTimeMs() + subtitle.getEndTimeMs()) / 2;
                
                if (subtitleMid >= sceneStart && subtitleMid < sceneEnd) {
                    if (sceneText.length() > 0) {
                        sceneText.append(" ");
                    }
                    sceneText.append(subtitle.getText());
                }
            }
            
            String scriptLine = sceneText.toString().trim();
            scriptLinesByScene.put(sceneNumber, scriptLine);
            
            if (!scriptLine.isEmpty()) {
                log.info("Scene {}: \"{}\"", sceneNumber, 
                    scriptLine.length() > 60 ? scriptLine.substring(0, 60) + "..." : scriptLine);
            }
        }
        
        return scriptLinesByScene;
    }
}
