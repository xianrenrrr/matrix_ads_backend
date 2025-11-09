package com.example.demo.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Alibaba Cloud OSS Storage Service
 * 
 * Replaces Firebase Storage with Alibaba Cloud OSS for better integration
 * with Alibaba Cloud AI services (Video Recognition, Qwen, etc.)
 * 
 * Bucket: xpectra
 * Region: cn-shanghai (华东2 - 上海)
 * Endpoint: oss-cn-shanghai.aliyuncs.com
 */
@Service
@ConditionalOnProperty(name = "alibaba.oss.enabled", havingValue = "true")
public class AlibabaOssStorageService {
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_ID:}")
    private String accessKeyId;
    
    @Value("${ALIBABA_CLOUD_ACCESS_KEY_SECRET:}")
    private String accessKeySecret;
    
    @Value("${alibaba.oss.bucket:xpectra}")
    private String bucketName;
    
    @Value("${alibaba.oss.endpoint:oss-cn-shanghai.aliyuncs.com}")
    private String endpoint;
    
    private OSS ossClient;
    
    @PostConstruct
    public void init() {
        if (accessKeyId == null || accessKeyId.isEmpty() || 
            accessKeySecret == null || accessKeySecret.isEmpty()) {
            throw new IllegalStateException("Alibaba Cloud OSS credentials not configured. " +
                "Set ALIBABA_CLOUD_ACCESS_KEY_ID and ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        }
        
        // Create OSS client
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        
        System.out.println("========================================");
        System.out.println("Alibaba OSS Storage Service Initialized");
        System.out.println("Bucket: " + bucketName);
        System.out.println("Region: cn-shanghai");
        System.out.println("Endpoint: " + endpoint);
        System.out.println("========================================");
    }
    
    @PreDestroy
    public void cleanup() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
    
    public static class UploadResult {
        public final String videoUrl;
        public final String thumbnailUrl;
        
        public UploadResult(String videoUrl, String thumbnailUrl) {
            this.videoUrl = videoUrl;
            this.thumbnailUrl = thumbnailUrl;
        }
    }
    
    /**
     * Upload video with thumbnail (matches Firebase interface)
     */
    public UploadResult uploadVideoWithThumbnail(MultipartFile file, String userId, String videoId) 
            throws IOException, InterruptedException {
        
        // Save video to temp file FIRST (before consuming the stream)
        java.io.File tempVideo = java.io.File.createTempFile("upload-", ".mp4");
        file.transferTo(tempVideo);
        
        // Upload video from temp file
        String videoObjectKey = String.format("videos/%s/%s/%s", userId, videoId, file.getOriginalFilename());
        String videoUrl = uploadFile(tempVideo, videoObjectKey, file.getContentType());
        
        System.out.println("[OSS] Uploaded video to: " + videoUrl);
        
        // Extract thumbnail using FFmpeg
        String thumbObjectKey = String.format("videos/%s/%s/thumbnail.jpg", userId, videoId);
        java.io.File tempThumb = java.io.File.createTempFile("thumb-", ".jpg");
        
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y", "-ss", "1", "-i", tempVideo.getAbsolutePath(), 
            "-frames:v", "1", tempThumb.getAbsolutePath()
        );
        Process proc = pb.start();
        int exitCode = proc.waitFor();
        
        if (exitCode != 0) {
            tempVideo.delete();
            tempThumb.delete();
            throw new IOException("Failed to extract thumbnail with FFmpeg");
        }
        
        // Upload thumbnail
        String thumbnailUrl = uploadFile(tempThumb, thumbObjectKey, "image/jpeg");
        
        System.out.println("[OSS] Uploaded thumbnail to: " + thumbnailUrl);
        
        // Clean up temp files
        tempVideo.delete();
        tempThumb.delete();
        
        return new UploadResult(videoUrl, thumbnailUrl);
    }
    
    /**
     * Upload file from InputStream
     */
    public String uploadFile(InputStream inputStream, String objectKey, String contentType) throws IOException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        
        PutObjectRequest putRequest = new PutObjectRequest(bucketName, objectKey, inputStream, metadata);
        ossClient.putObject(putRequest);
        
        // Return public URL (bucket is private, will need signed URLs for access)
        return String.format("https://%s.%s/%s", bucketName, endpoint, objectKey);
    }
    

    /**
     * Upload file from File object
     */
    public String uploadFile(java.io.File file, String objectKey, String contentType) throws IOException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        
        PutObjectRequest putRequest = new PutObjectRequest(bucketName, objectKey, file, metadata);
        ossClient.putObject(putRequest);
        
        return String.format("https://%s.%s/%s", bucketName, endpoint, objectKey);
    }
    
    /**
     * Generate signed URL with default 15 minutes expiration
     */
    public String generateSignedUrl(String ossUrl) {
        return generateSignedUrl(ossUrl, 15, TimeUnit.MINUTES);
    }
    
    /**
     * Generate signed URL with custom expiration
     * 
     * For Alibaba Cloud AI services, signed URLs work perfectly (unlike Firebase)
     * because all services are in the same cloud.
     */
    public String generateSignedUrl(String ossUrl, long duration, TimeUnit unit) {
        try {
            // Extract object key from OSS URL
            String objectKey = parseObjectKeyFromUrl(ossUrl);
            if (objectKey == null) {
                System.err.println("Invalid OSS URL: " + ossUrl);
                return ossUrl;
            }
            
            // Calculate expiration time
            long expirationMillis = System.currentTimeMillis() + unit.toMillis(duration);
            Date expiration = new Date(expirationMillis);
            
            // Generate signed URL
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectKey);
            request.setExpiration(expiration);
            
            URL signedUrl = ossClient.generatePresignedUrl(request);
            
            // Ensure HTTPS (Azure Video Indexer requires HTTPS)
            String signedUrlStr = signedUrl.toString();
            if (signedUrlStr.startsWith("http://")) {
                signedUrlStr = signedUrlStr.replace("http://", "https://");
                System.out.println("Converted signed URL to HTTPS for Azure compatibility");
            }
            
            System.out.println("Generated OSS signed URL for: " + objectKey + 
                             " (expires in " + duration + " " + unit + ")");
            
            return signedUrlStr;
        } catch (Exception e) {
            System.err.println("Error generating OSS signed URL: " + e.getMessage());
            return ossUrl;
        }
    }
    
    /**
     * Parse object key from OSS URL
     * Format: https://xpectra.oss-cn-shanghai.aliyuncs.com/path/to/object
     */
    private String parseObjectKeyFromUrl(String ossUrl) {
        try {
            if (ossUrl == null || !ossUrl.contains(bucketName)) {
                return null;
            }
            
            // Remove query parameters if present
            String cleanUrl = ossUrl.split("\\?")[0];
            
            // Extract object key after bucket domain
            String pattern = String.format("https://%s.%s/", bucketName, endpoint);
            if (cleanUrl.startsWith(pattern)) {
                return cleanUrl.substring(pattern.length());
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Delete object by URL
     */
    public boolean deleteObjectByUrl(String ossUrl) {
        try {
            String objectKey = parseObjectKeyFromUrl(ossUrl);
            if (objectKey == null) {
                return true; // Nothing to do
            }
            
            ossClient.deleteObject(bucketName, objectKey);
            System.out.println("[OSS] Deleted object: " + objectKey);
            return true;
        } catch (Exception e) {
            System.err.println("[OSS] Failed to delete object: " + ossUrl + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete all objects with prefix
     */
    public int deleteByPrefix(String prefix) {
        int deleted = 0;
        try {
            if (prefix == null) return 0;
            
            var objectListing = ossClient.listObjects(bucketName, prefix);
            var objects = objectListing.getObjectSummaries();
            
            for (var object : objects) {
                try {
                    ossClient.deleteObject(bucketName, object.getKey());
                    deleted++;
                } catch (Exception e) {
                    System.err.println("[OSS] Failed to delete: " + object.getKey() + " - " + e.getMessage());
                }
            }
            
            System.out.println("[OSS] Deleted " + deleted + " objects with prefix: " + prefix);
        } catch (Exception e) {
            System.err.println("[OSS] Failed to list/delete by prefix: " + prefix + " - " + e.getMessage());
        }
        return deleted;
    }
    
    /**
     * Upload keyframe image
     */
    public String uploadKeyframe(java.io.File keyframeFile, String userId, String videoId, int sceneNumber) 
            throws IOException {
        String objectKey = String.format("keyframes/%s/%s/scene-%d-keyframe.jpg", 
                                        userId, videoId, sceneNumber);
        return uploadFile(keyframeFile, objectKey, "image/jpeg");
    }
    
    /**
     * Upload compiled video
     */
    public String uploadCompiledVideo(java.io.File videoFile, String userId, String compilationId) 
            throws IOException {
        String objectKey = String.format("compiled/%s/%s/final-video.mp4", userId, compilationId);
        return uploadFile(videoFile, objectKey, "video/mp4");
    }
    
    /**
     * Prepare URL for Alibaba Cloud AI services access
     * 
     * For OSS URLs, generates a signed URL with specified expiration.
     * For non-OSS URLs, returns the original URL.
     * 
     * @param url Original URL (OSS or public URL)
     * @param duration Expiration duration
     * @param unit Time unit for duration
     * @return Accessible URL for Alibaba Cloud services
     */
    public String prepareUrlForAlibabaCloud(String url, long duration, TimeUnit unit) {
        if (url == null || url.isEmpty()) {
            System.out.println("[OSS] URL is null or empty");
            return url;
        }
        
        // Check if it's an OSS URL
        if (url.contains("aliyuncs.com")) {
            System.out.println("[OSS] Generating signed URL for Alibaba Cloud access");
            
            try {
                String signedUrl = generateSignedUrl(url, duration, unit);
                System.out.println("[OSS] Successfully generated signed URL (expires in " + duration + " " + unit + ")");
                return signedUrl;
            } catch (Exception e) {
                System.err.println("[OSS] Failed to generate signed URL: " + e.getMessage());
                System.err.println("[OSS] Falling back to original URL");
                return url;
            }
        }
        
        // Not an OSS URL, return as-is (assume it's publicly accessible)
        System.out.println("[OSS] Not an OSS URL, using original URL");
        return url;
    }
    
    /**
     * Download OSS file to local temp file
     * Centralizes OSS download logic for FFmpeg processing
     * 
     * @param ossUrl OSS URL to download
     * @param prefix Temp file prefix (e.g., "scene-", "video-")
     * @param suffix Temp file suffix (e.g., ".mp4", ".jpg")
     * @return Local temp file
     */
    public java.io.File downloadToTempFile(String ossUrl, String prefix, String suffix) throws IOException {
        String signedUrl = generateSignedUrl(ossUrl, 2, TimeUnit.HOURS);
        java.io.File tempFile = java.io.File.createTempFile(prefix, suffix);
        
        System.out.println("[OSS] Downloading " + ossUrl + " to " + tempFile.getAbsolutePath());
        
        try (java.io.InputStream in = new java.net.URL(signedUrl).openStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        
        System.out.println("[OSS] Downloaded successfully");
        return tempFile;
    }
    
    /**
     * Download multiple OSS files to local temp files
     * Optimized for batch downloads (e.g., scene videos for compilation)
     * 
     * @param ossUrls List of OSS URLs to download
     * @param prefix Temp file prefix
     * @param suffix Temp file suffix
     * @return List of local temp files (same order as input URLs)
     */
    public List<java.io.File> downloadMultipleToTempFiles(List<String> ossUrls, String prefix, String suffix) throws IOException {
        List<java.io.File> tempFiles = new ArrayList<>();
        
        for (int i = 0; i < ossUrls.size(); i++) {
            String url = ossUrls.get(i);
            java.io.File tempFile = downloadToTempFile(url, prefix + i + "-", suffix);
            tempFiles.add(tempFile);
        }
        
        System.out.println("[OSS] Downloaded " + tempFiles.size() + " files");
        return tempFiles;
    }
}
