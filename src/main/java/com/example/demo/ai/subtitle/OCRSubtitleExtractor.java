package com.example.demo.ai.subtitle;

import com.aliyun.ocr20191230.Client;
import com.aliyun.ocr20191230.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.demo.service.AlibabaOssStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts subtitle-like text from Alibaba Cloud Video OCR
 * 
 * API: RecognizeVideoCharacter (视频文字识别)
 * Documentation: https://help.aliyun.com/document_detail/442270.html
 * SDK: ocr20191230 (Tea SDK)
 * 
 * Strategy:
 * 1. Call Alibaba Cloud Video OCR API
 * 2. Group text elements by spatial location (subtitles appear in same area)
 * 3. Track text positions across frames to identify subtitle regions
 * 4. Extract text from subtitle regions chronologically
 */
@Service
public class OCRSubtitleExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(OCRSubtitleExtractor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_ID:}")
    private String accessKeyId;
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_SECRET:}")
    private String accessKeySecret;
    
    @Autowired(required = false)
    private AlibabaOssStorageService ossStorageService;
    
    private static final String ENDPOINT = "ocr.cn-shanghai.aliyuncs.com";
    
    /**
     * Extract subtitles from video using Alibaba Cloud Video OCR
     * 
     * @param videoUrl Video URL (OSS URL or public URL)
     * @return List of subtitle segments with timing
     */
    public List<SubtitleSegment> extract(String videoUrl) {
        log.info("Starting OCR subtitle extraction for video: {}", videoUrl);
        
        try {
            // Check credentials
            if (accessKeyId == null || accessKeyId.isEmpty() || 
                accessKeySecret == null || accessKeySecret.isEmpty()) {
                throw new IllegalStateException("Alibaba Cloud credentials not configured");
            }
            
            // Prepare video URL for Alibaba Cloud access
            String accessibleUrl = prepareVideoUrl(videoUrl);
            log.info("Prepared video URL for OCR");
            
            // Call Alibaba Cloud Video OCR API
            String ocrResultJson = callVideoOCR(accessibleUrl);
            
            // Parse result and extract subtitles
            List<SubtitleSegment> subtitles = parseOCRResult(ocrResultJson);
            
            log.info("OCR extraction completed. Found {} subtitle segments", subtitles.size());
            return subtitles;
            
        } catch (Exception e) {
            log.error("OCR subtitle extraction failed", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Prepare video URL for Alibaba Cloud access
     */
    private String prepareVideoUrl(String videoUrl) {
        if (ossStorageService == null) {
            log.warn("OSS service not available, using original URL");
            return videoUrl;
        }
        
        // Generate signed URL with 2 hour expiration for OCR processing
        return ossStorageService.prepareUrlForAlibabaCloud(videoUrl, 2, java.util.concurrent.TimeUnit.HOURS);
    }
    
    /**
     * Call Alibaba Cloud Video OCR API using Tea SDK
     * 
     * @param videoUrl Accessible video URL
     * @return OCR result JSON string
     */
    private String callVideoOCR(String videoUrl) throws Exception {
        log.info("Calling Alibaba Cloud Video OCR API");
        
        // Create OCR client
        Client client = createClient();
        
        try {
            // Build request
            RecognizeVideoCharacterRequest request = new RecognizeVideoCharacterRequest()
                .setVideoURL(videoUrl);
            
            // Runtime options
            RuntimeOptions runtime = new RuntimeOptions();
            
            // Call API
            RecognizeVideoCharacterResponse response = client.recognizeVideoCharacterWithOptions(request, runtime);
            
            if (response == null || response.getBody() == null) {
                throw new RuntimeException("Empty response from OCR API");
            }
            
            RecognizeVideoCharacterResponseBody body = response.getBody();
            
            // Check for errors
            if (body.getCode() != null && !"200".equals(body.getCode())) {
                throw new RuntimeException("OCR API error: " + body.getCode() + " - " + body.getMessage());
            }
            
            // Get result data
            String resultData = body.getData();
            if (resultData == null || resultData.isEmpty()) {
                log.warn("No OCR data returned");
                return "{}";
            }
            
            log.info("OCR API call successful, result length: {} chars", resultData.length());
            return resultData;
            
        } finally {
            // Close client
            if (client != null) {
                client.close();
            }
        }
    }
    
    /**
     * Create Alibaba Cloud OCR client using Tea SDK
     */
    private Client createClient() throws Exception {
        // Set credentials via system properties for credential client
        System.setProperty("ALIBABA_CLOUD_ACCESS_KEY_ID", accessKeyId);
        System.setProperty("ALIBABA_CLOUD_ACCESS_KEY_SECRET", accessKeySecret);
        
        // Create credential client (reads from system properties)
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        
        // Create config
        Config config = new Config()
            .setCredential(credential)
            .setEndpoint(ENDPOINT);
        
        return new Client(config);
    }
    
    /**
     * Parse OCR result JSON and extract subtitles
     * 
     * @param ocrResultJson OCR result from Alibaba Cloud
     * @return List of subtitle segments
     */
    private List<SubtitleSegment> parseOCRResult(String ocrResultJson) {
        try {
            JsonNode root = mapper.readTree(ocrResultJson);
            JsonNode framesNode = root.get("frames");
            
            if (framesNode == null || !framesNode.isArray()) {
                log.warn("No frames found in OCR result");
                return Collections.emptyList();
            }
            
            // Step 1: Parse all text elements with positions
            List<TextElement> allElements = new ArrayList<>();
            for (JsonNode frame : framesNode) {
                long timestamp = frame.get("timestamp").asLong();
                JsonNode elements = frame.get("elements");
                
                if (elements != null && elements.isArray()) {
                    for (JsonNode elem : elements) {
                        TextElement te = parseTextElement(elem, timestamp);
                        if (te != null) {
                            allElements.add(te);
                        }
                    }
                }
            }
            
            if (allElements.isEmpty()) {
                log.warn("No text elements found in OCR result");
                return Collections.emptyList();
            }
            
            log.info("Parsed {} text elements from OCR", allElements.size());
            
            // Step 2: Identify subtitle region (most common position)
            SubtitleRegion subtitleRegion = identifySubtitleRegion(allElements);
            log.info("Identified subtitle region: {}", subtitleRegion);
            
            // Step 3: Extract text from subtitle region
            List<SubtitleSegment> subtitles = extractFromRegion(allElements, subtitleRegion);
            log.info("Extracted {} subtitle segments from region", subtitles.size());
            
            return subtitles;
            
        } catch (Exception e) {
            log.error("Failed to parse OCR result", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Parse text element from OCR JSON
     */
    private TextElement parseTextElement(JsonNode elem, long timestamp) {
        try {
            JsonNode rect = elem.get("textRectangles");
            String text = elem.get("text").asText();
            double score = elem.get("score").asDouble();
            
            int top = rect.get("top").asInt();
            int left = rect.get("left").asInt();
            int width = rect.get("width").asInt();
            int height = rect.get("height").asInt();
            
            return new TextElement(text, timestamp, top, left, width, height, score);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Identify subtitle region by finding most common text position
     * Subtitles typically appear in bottom 1/3 of frame at consistent position
     */
    private SubtitleRegion identifySubtitleRegion(List<TextElement> elements) {
        // Group elements by approximate position (with tolerance)
        Map<String, List<TextElement>> positionGroups = new HashMap<>();
        
        for (TextElement elem : elements) {
            // Normalize position to grid (tolerance of 50 pixels)
            int gridTop = (elem.top / 50) * 50;
            int gridLeft = (elem.left / 50) * 50;
            String key = gridTop + "," + gridLeft;
            
            positionGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(elem);
        }
        
        // Find position with most elements (likely subtitle region)
        String bestKey = null;
        int maxCount = 0;
        
        for (Map.Entry<String, List<TextElement>> entry : positionGroups.entrySet()) {
            if (entry.getValue().size() > maxCount) {
                maxCount = entry.getValue().size();
                bestKey = entry.getKey();
            }
        }
        
        if (bestKey == null) {
            // Fallback: bottom center region
            return new SubtitleRegion(250, 150, 100, 50);
        }
        
        // Calculate average position from best group
        List<TextElement> bestGroup = positionGroups.get(bestKey);
        int avgTop = (int) bestGroup.stream().mapToInt(e -> e.top).average().orElse(250);
        int avgLeft = (int) bestGroup.stream().mapToInt(e -> e.left).average().orElse(150);
        
        return new SubtitleRegion(avgTop, avgLeft, 150, 80);
    }
    
    /**
     * Extract text from identified subtitle region
     */
    private List<SubtitleSegment> extractFromRegion(List<TextElement> allElements, SubtitleRegion region) {
        // Filter elements in subtitle region
        List<TextElement> subtitleElements = allElements.stream()
            .filter(e -> region.contains(e))
            .filter(e -> e.score > 0.7) // High confidence only
            .sorted(Comparator.comparingLong(e -> e.timestamp))
            .collect(Collectors.toList());
        
        // Group consecutive elements into subtitle segments
        List<SubtitleSegment> subtitles = new ArrayList<>();
        
        if (subtitleElements.isEmpty()) {
            return subtitles;
        }
        
        long currentStart = subtitleElements.get(0).timestamp;
        StringBuilder currentText = new StringBuilder();
        long lastTimestamp = currentStart;
        
        for (TextElement elem : subtitleElements) {
            // If gap > 1 second, start new subtitle
            if (elem.timestamp - lastTimestamp > 1000) {
                if (currentText.length() > 0) {
                    subtitles.add(new SubtitleSegment(
                        currentStart,
                        lastTimestamp,
                        currentText.toString().trim(),
                        1.0
                    ));
                }
                currentStart = elem.timestamp;
                currentText = new StringBuilder();
            }
            
            currentText.append(elem.text).append(" ");
            lastTimestamp = elem.timestamp;
        }
        
        // Add last subtitle
        if (currentText.length() > 0) {
            subtitles.add(new SubtitleSegment(
                currentStart,
                lastTimestamp,
                currentText.toString().trim(),
                1.0
            ));
        }
        
        return subtitles;
    }
    
    /**
     * Group OCR subtitles by scene timing and return scriptLine per scene
     * 
     * This method takes OCR subtitle segments and scene timing information,
     * then assigns OCR text to each scene based on timestamp overlap.
     * 
     * @param ocrSubtitles List of OCR subtitle segments with timing
     * @param scenes List of scenes with startTimeMs and endTimeMs
     * @return Map of scene number to scriptLine text
     */
    public Map<Integer, String> groupByScenes(List<SubtitleSegment> ocrSubtitles, List<Map<String, Object>> scenes) {
        Map<Integer, String> scriptLinesByScene = new HashMap<>();
        
        if (ocrSubtitles == null || ocrSubtitles.isEmpty() || scenes == null || scenes.isEmpty()) {
            log.warn("Empty OCR subtitles or scenes, returning empty map");
            return scriptLinesByScene;
        }
        
        log.info("Grouping {} OCR subtitles into {} scenes", ocrSubtitles.size(), scenes.size());
        
        // Group OCR segments by scene
        for (int i = 0; i < scenes.size(); i++) {
            Map<String, Object> scene = scenes.get(i);
            int sceneNumber = ((Number) scene.get("sceneNumber")).intValue();
            long sceneStart = ((Number) scene.get("startMs")).longValue();
            long sceneEnd = ((Number) scene.get("endMs")).longValue();
            
            StringBuilder sceneText = new StringBuilder();
            
            // Find OCR segments that overlap with this scene
            for (SubtitleSegment ocr : ocrSubtitles) {
                long ocrMid = (ocr.getStartTimeMs() + ocr.getEndTimeMs()) / 2;
                
                // Check if OCR segment midpoint falls within scene time range
                if (ocrMid >= sceneStart && ocrMid < sceneEnd) {
                    if (sceneText.length() > 0) {
                        sceneText.append(" ");
                    }
                    sceneText.append(ocr.getText());
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
    
    /**
     * Text element from OCR
     */
    private static class TextElement {
        String text;
        long timestamp;
        int top, left, width, height;
        double score;
        
        TextElement(String text, long timestamp, int top, int left, int width, int height, double score) {
            this.text = text;
            this.timestamp = timestamp;
            this.top = top;
            this.left = left;
            this.width = width;
            this.height = height;
            this.score = score;
        }
    }
    
    /**
     * Identified subtitle region
     */
    private static class SubtitleRegion {
        int centerTop, centerLeft, toleranceWidth, toleranceHeight;
        
        SubtitleRegion(int centerTop, int centerLeft, int toleranceWidth, int toleranceHeight) {
            this.centerTop = centerTop;
            this.centerLeft = centerLeft;
            this.toleranceWidth = toleranceWidth;
            this.toleranceHeight = toleranceHeight;
        }
        
        boolean contains(TextElement elem) {
            int elemCenterTop = elem.top + elem.height / 2;
            int elemCenterLeft = elem.left + elem.width / 2;
            
            return Math.abs(elemCenterTop - centerTop) <= toleranceHeight &&
                   Math.abs(elemCenterLeft - centerLeft) <= toleranceWidth;
        }
        
        @Override
        public String toString() {
            return String.format("top=%d, left=%d, tolerance=%dx%d", 
                centerTop, centerLeft, toleranceWidth, toleranceHeight);
        }
    }
    
}
