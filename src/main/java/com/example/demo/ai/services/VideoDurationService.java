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
 * Service for extracting video duration using ffprobe
 */
@Service
public class VideoDurationService {
    private static final Logger log = LoggerFactory.getLogger(VideoDurationService.class);
    
    @Autowired
    private GcsFileResolver gcsFileResolver;
    
    @Value("${ffprobe.path:ffprobe}")
    private String ffprobePath;
    
    /**
     * Get video duration in seconds
     * @param videoUrl URL or path to the video file
     * @return Duration in seconds, or null if extraction fails
     */
    public Double getVideoDuration(String videoUrl) {
        try {
            // Resolve GCS URL to local file
            try (GcsFileResolver.ResolvedFile resolvedFile = gcsFileResolver.resolve(videoUrl)) {
                String localPath = resolvedFile.getPathAsString();
                
                ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=nk=1:nw=1",
                    localPath
                );
                
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String durationLine = reader.readLine();
                    
                    if (durationLine != null && !durationLine.trim().isEmpty()) {
                        double duration = Double.parseDouble(durationLine.trim());
                        log.info("Extracted video duration: {:.2f} seconds for {}", duration, videoUrl);
                        return duration;
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.warn("ffprobe exited with code: {} for {}", exitCode, videoUrl);
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract video duration for {}: {}", videoUrl, e.getMessage());
        }
        
        return null;
    }
}
