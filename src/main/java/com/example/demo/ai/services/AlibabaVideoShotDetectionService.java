package com.example.demo.ai.services;

import com.aliyun.credentials.Client;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.videorecog20200320.models.DetectVideoShotRequest;
import com.aliyun.videorecog20200320.models.DetectVideoShotResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.demo.model.SceneSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Alibaba Cloud Video Shot Detection Service
 * 
 * Uses Alibaba Cloud Video Recognition API (Tea SDK) to detect scene changes in videos.
 * Replaces FFmpeg-based scene detection with cloud-based AI detection.
 * 
 * API Documentation: https://help.aliyun.com/document_detail/151777.html
 * SDK Reference: videorecog20200320 (Tea SDK)
 * 
 * Pricing: ~¥0.05 per minute of video
 */
@Service
public class AlibabaVideoShotDetectionService {
    private static final Logger log = LoggerFactory.getLogger(AlibabaVideoShotDetectionService.class);
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_ID:}")
    private String accessKeyId;
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_SECRET:}")
    private String accessKeySecret;
    
    private static final String ENDPOINT = "videorecog.cn-shanghai.aliyuncs.com";
    
    /**
     * Detect scene shots in a video
     * 
     * @param videoUrl Public URL of the video to analyze
     * @return List of scene segments with start/end times
     * @throws Exception if detection fails
     */
    public List<SceneSegment> detectScenes(String videoUrl) throws Exception {
        log.info("Starting Alibaba Cloud shot detection for video: {}", videoUrl);
        
        if (accessKeyId == null || accessKeyId.isEmpty() || 
            accessKeySecret == null || accessKeySecret.isEmpty()) {
            throw new IllegalStateException("Alibaba Cloud credentials not configured. " +
                "Set ALIBABA_CLOUD_ACCESS_KEY_ID and ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        }
        
        try {
            // Create client
            com.aliyun.videorecog20200320.Client client = createClient();
            
            // Build request
            DetectVideoShotRequest request = new DetectVideoShotRequest();
            request.setVideoUrl(videoUrl);
            
            // Call API
            RuntimeOptions runtime = new RuntimeOptions();
            DetectVideoShotResponse response = client.detectVideoShotWithOptions(request, runtime);
            
            // Parse response
            List<SceneSegment> segments = parseResponse(response);
            
            log.info("Shot detection completed. Found {} scenes", segments.size());
            return segments;
            
        } catch (Exception e) {
            log.error("Shot detection failed", e);
            throw e;
        }
    }
    
    /**
     * Create Alibaba Cloud client using Tea SDK
     */
    private com.aliyun.videorecog20200320.Client createClient() throws Exception {
        Client credential = new Client();
        Config config = new Config();
        config.setCredential(credential);
        config.setEndpoint(ENDPOINT);
        
        // Set credentials via system properties (Tea SDK reads from these)
        System.setProperty("ALIBABA_CLOUD_ACCESS_KEY_ID", accessKeyId);
        System.setProperty("ALIBABA_CLOUD_ACCESS_KEY_SECRET", accessKeySecret);
        
        return new com.aliyun.videorecog20200320.Client(config);
    }
    
    /**
     * Parse Alibaba Cloud response into SceneSegment list
     */
    private List<SceneSegment> parseResponse(DetectVideoShotResponse response) {
        List<SceneSegment> segments = new ArrayList<>();
        
        if (response == null || response.getBody() == null) {
            log.warn("Empty response from Alibaba Cloud");
            return segments;
        }
        
        // Log the full response for debugging
        try {
            log.info("Response body: {}", com.aliyun.teautil.Common.toJSONString(response.getBody()));
        } catch (Exception e) {
            log.warn("Could not serialize response: {}", e.getMessage());
        }
        
        // Get response body
        var body = response.getBody();
        
        // Check for errors (if code field exists)
        // Note: Successful responses may not have a code field
        
        // Parse shot list from data field
        if (body.getData() == null) {
            log.warn("No shot data in response");
            return segments;
        }
        
        // The data field contains DetectVideoShotResponseBodyData object
        try {
            var data = body.getData();
            
            // Get shot frame IDs from the data object
            List<Integer> shotFrameIds = data.getShotFrameIds();
            
            if (shotFrameIds == null || shotFrameIds.isEmpty()) {
                log.warn("No shot frame IDs found in response");
                return segments;
            }
            
            log.info("Found {} shot frame IDs from Alibaba Cloud response", shotFrameIds.size());
            
            // Convert frame IDs to time segments (assuming 30fps)
            for (int i = 0; i < shotFrameIds.size() - 1; i++) {
                try {
                    int startFrame = shotFrameIds.get(i);
                    int endFrame = shotFrameIds.get(i + 1);
                    
                    // Convert frames to milliseconds (assuming 30fps)
                    long startMs = (long) (startFrame / 30.0 * 1000);
                    long endMs = (long) (endFrame / 30.0 * 1000);
                    
                    SceneSegment segment = new SceneSegment();
                    segment.setStartTimeMs(startMs);
                    segment.setEndTimeMs(endMs);
                    segment.setLabels(new ArrayList<>());
                    segment.setPersonPresent(false);
                    
                    segments.add(segment);
                    
                    log.debug("Scene {}: {}ms - {}ms (frames {}-{})", segments.size(), startMs, endMs, startFrame, endFrame);
                } catch (Exception e) {
                    log.warn("Failed to parse shot: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse data: {}", e.getMessage(), e);
        }
        
        return segments;
    }
}
