package com.example.demo.ai.services;

import com.example.demo.ai.shared.GcsFileResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Service for extracting video metadata (resolution, aspect ratio, duration)
 */
@Service
public class VideoMetadataService {
    private static final Logger log = LoggerFactory.getLogger(VideoMetadataService.class);
    
    @Autowired
    private GcsFileResolver gcsFileResolver;
    
    @Value("${ffprobe.path:ffprobe}")
    private String ffprobePath;
    
    /**
     * Video metadata container
     */
    public static class VideoMetadata {
        public int width;
        public int height;
        public double durationSeconds;
        public String format;  // e.g., "1080x1920 (9:16)" or "1920x1080 (16:9)"
        public String orientation;  // "portrait" or "landscape"
        
        public VideoMetadata(int width, int height, double durationSeconds) {
            this.width = width;
            this.height = height;
            this.durationSeconds = durationSeconds;
            this.orientation = height >= width ? "portrait" : "landscape";
            this.format = buildFormatString();
        }
        
        private String buildFormatString() {
            // Determine resolution label
            String resolution;
            int maxDim = Math.max(width, height);
            if (maxDim >= 2160) {
                resolution = "4K";
            } else if (maxDim >= 1080) {
                resolution = "1080p";
            } else if (maxDim >= 720) {
                resolution = "720p";
            } else {
                resolution = maxDim + "p";
            }
            
            // Calculate aspect ratio
            int gcd = gcd(width, height);
            int aspectW = width / gcd;
            int aspectH = height / gcd;
            
            // Common aspect ratios
            String aspect;
            if (aspectW == 16 && aspectH == 9) {
                aspect = "16:9";
            } else if (aspectW == 9 && aspectH == 16) {
                aspect = "9:16";
            } else if (aspectW == 4 && aspectH == 3) {
                aspect = "4:3";
            } else if (aspectW == 3 && aspectH == 4) {
                aspect = "3:4";
            } else if (aspectW == 1 && aspectH == 1) {
                aspect = "1:1";
            } else {
                aspect = aspectW + ":" + aspectH;
            }
            
            return resolution + " " + aspect;
        }
        
        private int gcd(int a, int b) {
            return b == 0 ? a : gcd(b, a % b);
        }
    }
    
    /**
     * Extract video metadata using ffprobe
     * @param videoUrl URL or path to the video file
     * @return VideoMetadata with resolution, aspect ratio, and duration
     */
    public VideoMetadata getVideoMetadata(String videoUrl) {
        log.info(">>> VideoMetadataService.getVideoMetadata() called");
        log.info(">>> Input videoUrl: {}", videoUrl);
        
        try {
            // Resolve GCS URL to local file
            log.info(">>> Resolving GCS URL to local file...");
            try (GcsFileResolver.ResolvedFile resolvedFile = gcsFileResolver.resolve(videoUrl)) {
                String localPath = resolvedFile.getPathAsString();
                log.info(">>> Resolved to local path: {}", localPath);
                
                // Get width, height, and duration using ffprobe
                String command = String.format("%s -v error -select_streams v:0 -show_entries stream=width,height:format=duration -of csv=p=0 %s", 
                                              ffprobePath, localPath);
                log.info(">>> Executing ffprobe command: {}", command);
                
                ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath,
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=width,height:format=duration",
                    "-of", "csv=p=0",
                    localPath
                );
                
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    log.info(">>> FFprobe output: {}", line);
                    
                    if (line != null && !line.trim().isEmpty()) {
                        String[] parts = line.split(",");
                        log.info(">>> Parsed parts: {}", String.join(", ", parts));
                        
                        if (parts.length >= 3) {
                            int width = Integer.parseInt(parts[0].trim());
                            int height = Integer.parseInt(parts[1].trim());
                            double duration = Double.parseDouble(parts[2].trim());
                            
                            VideoMetadata metadata = new VideoMetadata(width, height, duration);
                            log.info("✅ SUCCESS: Extracted video metadata: {}x{} ({}) - {:.2f}s", 
                                    width, height, metadata.format, duration);
                            return metadata;
                        } else {
                            log.error("❌ FAILED: FFprobe output has insufficient parts: {}", parts.length);
                        }
                    } else {
                        log.error("❌ FAILED: FFprobe returned empty output");
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.error("❌ FAILED: ffprobe exited with code: {} for {}", exitCode, videoUrl);
                    
                    // Read error stream
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            log.error(">>> FFprobe error: {}", errorLine);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ EXCEPTION: Failed to extract video metadata for {}: {}", videoUrl, e.getMessage(), e);
        }
        
        log.warn(">>> Returning null (extraction failed)");
        return null;
    }
    
    /**
     * Get human-readable orientation instruction based on video metadata
     * @param metadata Video metadata
     * @param language Language for instruction (zh-CN or en)
     * @return Instruction string like "竖着拍" or "横着拍"
     */
    public String getOrientationInstruction(VideoMetadata metadata, String language) {
        if (metadata == null) return null;
        
        boolean zh = "zh".equalsIgnoreCase(language) || "zh-CN".equalsIgnoreCase(language);
        
        if ("portrait".equals(metadata.orientation)) {
            return zh ? "竖着拍" : "Portrait";
        } else {
            return zh ? "横着拍" : "Landscape";
        }
    }
}
