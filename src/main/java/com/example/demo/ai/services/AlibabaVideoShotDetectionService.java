package com.example.demo.ai.services;

import com.aliyun.credentials.Client;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.videorecog20200320.models.DetectVideoShotRequest;
import com.aliyun.videorecog20200320.models.DetectVideoShotResponse;
import com.aliyun.videorecog20200320.models.GetAsyncJobResultRequest;
import com.aliyun.videorecog20200320.models.GetAsyncJobResultResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.demo.model.SceneSegment;
import com.example.demo.service.FirebaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
 * 
 * Security: Uses 7-day signed URLs instead of public URLs for Firebase Storage videos
 */
@Service
public class AlibabaVideoShotDetectionService {
    private static final Logger log = LoggerFactory.getLogger(AlibabaVideoShotDetectionService.class);
    
    @Autowired(required = false)
    private FirebaseStorageService firebaseStorageService;
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_ID:}")
    private String accessKeyId;
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_SECRET:}")
    private String accessKeySecret;
    
    private static final String ENDPOINT = "videorecog.cn-shanghai.aliyuncs.com";
    
    /**
     * Detect scene shots in a video (async with polling)
     * 
     * @param videoUrl Firebase Storage URL or public URL of the video to analyze
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
            // Convert Firebase Storage URL to 7-day signed URL for Alibaba Cloud access
            String accessibleUrl = prepareVideoUrl(videoUrl);
            
            // Create client
            com.aliyun.videorecog20200320.Client client = createClient();
            
            // Step 1: Submit async job (API is async by default)
            DetectVideoShotRequest request = new DetectVideoShotRequest();
            request.setVideoUrl(accessibleUrl);
            
            RuntimeOptions runtime = new RuntimeOptions();
            DetectVideoShotResponse response = client.detectVideoShotWithOptions(request, runtime);
            
            // Get job ID from response
            String jobId = extractJobId(response);
            if (jobId == null) {
                log.error("Failed to get job ID from async response");
                return new ArrayList<>();
            }
            
            log.info("Async job submitted with ID: {}", jobId);
            
            // Step 2: Poll for results
            List<SceneSegment> segments = pollForResults(client, jobId);
            
            log.info("Shot detection completed. Found {} scenes", segments.size());
            return segments;
            
        } catch (Exception e) {
            log.error("Shot detection failed", e);
            throw e;
        }
    }
    
    /**
     * Extract job ID from async response
     */
    private String extractJobId(DetectVideoShotResponse response) {
        try {
            if (response != null && response.getBody() != null) {
                String requestId = response.getBody().getRequestId();
                log.info("Got requestId (jobId): {}", requestId);
                return requestId;
            }
        } catch (Exception e) {
            log.error("Failed to extract job ID: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Poll for async job results
     */
    private List<SceneSegment> pollForResults(com.aliyun.videorecog20200320.Client client, String jobId) throws Exception {
        int maxAttempts = 60; // Max 5 minutes (60 * 5 seconds)
        int pollInterval = 5000; // 5 seconds
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Query job status
                GetAsyncJobResultRequest request = new GetAsyncJobResultRequest();
                request.setJobId(jobId);
                
                RuntimeOptions runtime = new RuntimeOptions();
                GetAsyncJobResultResponse response = client.getAsyncJobResultWithOptions(request, runtime);
                
                if (response == null || response.getBody() == null) {
                    log.warn("Empty response from GetAsyncJobResult");
                    Thread.sleep(pollInterval);
                    continue;
                }
                
                var body = response.getBody();
                
                // Log response for debugging
                log.info("GetAsyncJobResult response: {}", com.aliyun.teautil.Common.toJSONString(body));
                
                // Check if data is available (indicates completion or failure)
                if (body.getData() != null) {
                    var data = body.getData();
                    
                    // Check for failure status
                    if (data.getStatus() != null && "PROCESS_FAILED".equals(data.getStatus())) {
                        log.error("Job failed: {} - {}", data.getErrorCode(), data.getErrorMessage());
                        return new ArrayList<>();
                    }
                    
                    // Check for success status
                    if (data.getStatus() != null && "PROCESS_SUCCESS".equals(data.getStatus())) {
                        log.info("Job completed successfully, parsing results");
                        return parseAsyncResults(response);
                    }
                    
                    // Unknown status, wait and retry
                    log.info("Job status: {} (attempt {}/{}), waiting...", data.getStatus(), attempt, maxAttempts);
                    Thread.sleep(pollInterval);
                } else {
                    // Still processing, wait and retry
                    log.info("Job still processing (attempt {}/{}), waiting...", attempt, maxAttempts);
                    Thread.sleep(pollInterval);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            } catch (Exception e) {
                log.warn("Error polling job status (attempt {}): {}", attempt, e.getMessage());
                Thread.sleep(pollInterval);
            }
        }
        
        log.error("Job timed out after {} attempts", maxAttempts);
        return new ArrayList<>();
    }
    
    /**
     * Prepare video URL for Alibaba Cloud access
     * 
     * If the URL is a Firebase Storage URL, generates a 7-day signed URL for secure access.
     * Otherwise, returns the URL as-is (assumes it's already publicly accessible).
     * 
     * @param videoUrl Original video URL (Firebase Storage or public URL)
     * @return Accessible URL for Alibaba Cloud (signed URL or original)
     */
    private String prepareVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return videoUrl;
        }
        
        // Check if it's a Firebase Storage URL
        if (videoUrl.contains("storage.googleapis.com") && firebaseStorageService != null) {
            log.info("Converting Firebase Storage URL to 7-day signed URL for Alibaba Cloud access");
            
            // Generate 7-day signed URL (enough time for Alibaba Cloud to process the video)
            String signedUrl = firebaseStorageService.generateSignedUrl(videoUrl, 7, TimeUnit.DAYS);
            
            log.info("Generated 7-day signed URL (expires in 7 days)");
            return signedUrl;
        }
        
        // Not a Firebase URL or service not available, return as-is
        log.info("Using original URL (not a Firebase Storage URL or service unavailable)");
        return videoUrl;
    }
    
    /**
     * Create Alibaba Cloud client using Tea SDK (matches official sample)
     */
    private com.aliyun.videorecog20200320.Client createClient() throws Exception {
        // Set credentials via system properties (Tea SDK credential client reads from these)
        System.setProperty("ALIBABA_CLOUD_ACCESS_KEY_ID", accessKeyId);
        System.setProperty("ALIBABA_CLOUD_ACCESS_KEY_SECRET", accessKeySecret);
        
        // Create credential client (reads from system properties)
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        
        // Create config with credential
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setCredential(credential);
        
        // Set endpoint
        config.endpoint = ENDPOINT;
        
        return new com.aliyun.videorecog20200320.Client(config);
    }
    
    /**
     * Parse async job results into SceneSegment list
     */
    private List<SceneSegment> parseAsyncResults(GetAsyncJobResultResponse response) {
        List<SceneSegment> segments = new ArrayList<>();
        
        try {
            if (response == null || response.getBody() == null) {
                log.warn("Empty async result response");
                return segments;
            }
            
            var body = response.getBody();
            
            // Log full response for debugging
            log.info("Async result: {}", com.aliyun.teautil.Common.toJSONString(body));
            
            // Parse data field which contains the shot detection results
            if (body.getData() == null) {
                log.warn("No data in async result");
                return segments;
            }
            
            var data = body.getData();
            
            // The data object contains the result - need to serialize and parse it
            String dataJson = com.aliyun.teautil.Common.toJSONString(data);
            log.info("Data JSON: {}", dataJson);
            
            // Parse JSON to extract ShotFrameIds
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> dataMap = mapper.readValue(dataJson, java.util.Map.class);
            
            @SuppressWarnings("unchecked")
            List<Integer> shotFrameIds = (List<Integer>) dataMap.get("ShotFrameIds");
            
            if (shotFrameIds == null || shotFrameIds.isEmpty()) {
                log.warn("No shot frame IDs in async result");
                return segments;
            }
            
            log.info("Found {} shot frame IDs from async result", shotFrameIds.size());
            
            // Convert frame IDs to time segments (assuming 30fps)
            for (int i = 0; i < shotFrameIds.size() - 1; i++) {
                int startFrame = shotFrameIds.get(i);
                int endFrame = shotFrameIds.get(i + 1);
                
                long startMs = (long) (startFrame / 30.0 * 1000);
                long endMs = (long) (endFrame / 30.0 * 1000);
                
                SceneSegment segment = new SceneSegment();
                segment.setStartTimeMs(startMs);
                segment.setEndTimeMs(endMs);
                segment.setLabels(new ArrayList<>());
                segment.setPersonPresent(false);
                
                segments.add(segment);
                
                log.debug("Scene {}: {}ms - {}ms (frames {}-{})", 
                    segments.size(), startMs, endMs, startFrame, endFrame);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse async results: {}", e.getMessage(), e);
        }
        
        return segments;
    }
    
    /**
     * Parse Alibaba Cloud response into SceneSegment list (sync mode - not used)
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
