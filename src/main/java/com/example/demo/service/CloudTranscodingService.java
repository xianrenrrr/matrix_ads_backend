package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Cloud-based video transcoding service using Alibaba Cloud MPS.
 * 
 * This service transcodes videos to H.264 baseline profile for WeChat compatibility,
 * running in the cloud to avoid OOM issues on memory-limited servers.
 * 
 * To enable:
 * 1. Set ALIYUN_MPS_ENABLED=true
 * 2. Set ALIYUN_MPS_PIPELINE_ID=your-pipeline-id
 * 3. Optionally set ALIYUN_MPS_TEMPLATE_ID for custom template
 * 
 * @see https://www.alibabacloud.com/help/en/mps/developer-reference/api-mts-2014-06-18-submitjobs
 */
@Service
public class CloudTranscodingService {
    
    private static final Logger log = LoggerFactory.getLogger(CloudTranscodingService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${ALIYUN_MPS_ENABLED:false}")
    private boolean enabled;
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_ID:}")
    private String accessKeyId;
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_SECRET:}")
    private String accessKeySecret;
    
    @Value("${ALIYUN_OSS_REGION:ap-southeast-1}")
    private String region;
    
    @Value("${ALIYUN_OSS_BUCKET:xpectra1}")
    private String bucket;
    
    @Value("${ALIYUN_MPS_PIPELINE_ID:}")
    private String pipelineId;
    
    @Value("${ALIYUN_MPS_TEMPLATE_ID:}")
    private String templateId;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    /**
     * Check if cloud transcoding is enabled and configured.
     */
    public boolean isEnabled() {
        boolean configured = enabled && 
            pipelineId != null && !pipelineId.isEmpty() &&
            accessKeyId != null && !accessKeyId.isEmpty();
        
        if (enabled && !configured) {
            log.warn("Cloud transcoding enabled but not fully configured. Check ALIYUN_MPS_PIPELINE_ID");
        }
        
        return configured;
    }
    
    /**
     * Submit a video for cloud transcoding to H.264 baseline profile.
     * This is async - returns immediately with a job ID.
     * 
     * @param inputOssPath OSS path of input video (e.g., "videos/user/video.mp4")
     * @param outputOssPath OSS path for transcoded output (e.g., "videos/user/video_transcoded.mp4")
     * @return Job ID if submitted successfully, null if disabled or failed
     */
    public String submitTranscodeJob(String inputOssPath, String outputOssPath) {
        if (!isEnabled()) {
            log.debug("Cloud transcoding is disabled or not configured");
            return null;
        }
        
        try {
            log.info("Submitting MPS transcode job: {} -> {}", inputOssPath, outputOssPath);
            
            // Build input JSON
            Map<String, String> inputMap = new LinkedHashMap<>();
            inputMap.put("Bucket", bucket);
            inputMap.put("Location", "oss-" + region);
            inputMap.put("Object", inputOssPath);
            String inputJson = objectMapper.writeValueAsString(inputMap);
            
            // Build output JSON with transcoding config
            Map<String, Object> outputMap = new LinkedHashMap<>();
            outputMap.put("OutputObject", outputOssPath);
            
            // Use template if provided, otherwise use inline config for WeChat
            if (templateId != null && !templateId.isEmpty()) {
                outputMap.put("TemplateId", templateId);
            } else {
                // Inline config for H.264 baseline (WeChat compatible)
                Map<String, Object> container = Map.of("Format", "mp4");
                Map<String, Object> video = Map.of(
                    "Codec", "H.264",
                    "Profile", "baseline",
                    "Bitrate", "1500",
                    "Width", "1280",
                    "Fps", "30"
                );
                Map<String, Object> audio = Map.of(
                    "Codec", "AAC",
                    "Bitrate", "128",
                    "Samplerate", "44100"
                );
                outputMap.put("Container", container);
                outputMap.put("Video", video);
                outputMap.put("Audio", audio);
            }
            
            String outputsJson = "[" + objectMapper.writeValueAsString(outputMap) + "]";
            
            // Build API request parameters
            Map<String, String> params = new TreeMap<>();
            params.put("Action", "SubmitJobs");
            params.put("Input", inputJson);
            params.put("OutputBucket", bucket);
            params.put("OutputLocation", "oss-" + region);
            params.put("Outputs", outputsJson);
            params.put("PipelineId", pipelineId);
            
            // Make API call
            String response = callMpsApi(params);
            
            // Parse response
            JsonNode root = objectMapper.readTree(response);
            if (root.has("JobResultList") && root.get("JobResultList").has("JobResult")) {
                JsonNode jobResults = root.get("JobResultList").get("JobResult");
                if (jobResults.isArray() && jobResults.size() > 0) {
                    JsonNode job = jobResults.get(0).get("Job");
                    if (job != null && job.has("JobId")) {
                        String jobId = job.get("JobId").asText();
                        log.info("✅ MPS transcode job submitted: {}", jobId);
                        return jobId;
                    }
                }
            }
            
            log.error("MPS job submission failed. Response: {}", response);
            return null;
            
        } catch (Exception e) {
            log.error("Failed to submit MPS transcode job: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check the status of a transcoding job.
     * 
     * @param jobId The job ID from submitTranscodeJob
     * @return Status: "Success", "Fail", "Submitted", "Transcoding", or null if error
     */
    public String getJobStatus(String jobId) {
        if (!isEnabled() || jobId == null) {
            return null;
        }
        
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("Action", "QueryJobList");
            params.put("JobIds", jobId);
            
            String response = callMpsApi(params);
            
            JsonNode root = objectMapper.readTree(response);
            if (root.has("JobList") && root.get("JobList").has("Job")) {
                JsonNode jobs = root.get("JobList").get("Job");
                if (jobs.isArray() && jobs.size() > 0) {
                    String state = jobs.get(0).get("State").asText();
                    log.debug("MPS job {} status: {}", jobId, state);
                    return state;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Failed to query MPS job status: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Wait for a transcoding job to complete (with timeout).
     * 
     * @param jobId The job ID
     * @param timeoutSeconds Maximum time to wait
     * @return true if job completed successfully, false otherwise
     */
    public boolean waitForJob(String jobId, int timeoutSeconds) {
        if (jobId == null) return false;
        
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            String status = getJobStatus(jobId);
            
            if ("Success".equals(status)) {
                log.info("✅ MPS job {} completed successfully", jobId);
                return true;
            } else if ("Fail".equals(status)) {
                log.error("❌ MPS job {} failed", jobId);
                return false;
            }
            
            // Wait before polling again
            try {
                Thread.sleep(2000); // Poll every 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        log.warn("MPS job {} timed out after {}s", jobId, timeoutSeconds);
        return false;
    }
    
    /**
     * Call Alibaba Cloud MPS API with signature.
     */
    private String callMpsApi(Map<String, String> params) throws Exception {
        // Add common parameters
        params.put("Format", "JSON");
        params.put("Version", "2014-06-18");
        params.put("AccessKeyId", accessKeyId);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        params.put("Timestamp", sdf.format(new Date()));
        
        // Build signature
        String signature = sign(params);
        params.put("Signature", signature);
        
        // Build URL
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("https://mts.").append(region).append(".aliyuncs.com/?");
        
        for (Map.Entry<String, String> entry : params.entrySet()) {
            urlBuilder.append(percentEncode(entry.getKey()))
                     .append("=")
                     .append(percentEncode(entry.getValue()))
                     .append("&");
        }
        
        String url = urlBuilder.substring(0, urlBuilder.length() - 1);
        
        // Make HTTP request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            log.error("MPS API error: {} - {}", response.statusCode(), response.body());
        }
        
        return response.body();
    }
    
    /**
     * Generate Alibaba Cloud API signature.
     */
    private String sign(Map<String, String> params) throws Exception {
        // Sort parameters
        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        
        // Build canonical query string
        StringBuilder queryBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            queryBuilder.append("&")
                       .append(percentEncode(entry.getKey()))
                       .append("=")
                       .append(percentEncode(entry.getValue()));
        }
        String canonicalQuery = queryBuilder.substring(1);
        
        // Build string to sign
        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonicalQuery);
        
        // Sign with HMAC-SHA1
        String key = accessKeySecret + "&";
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(signData);
    }
    
    /**
     * URL encode for Alibaba Cloud API.
     */
    private String percentEncode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~");
    }
}
