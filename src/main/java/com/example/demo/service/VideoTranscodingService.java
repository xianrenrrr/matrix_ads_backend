package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Service for transcoding videos to WeChat mini program compatible format.
 * 
 * WeChat mini program video requirements:
 * - Video codec: H.264 (baseline or main profile)
 * - Audio codec: AAC
 * - Container: MP4
 * - Resolution: Up to 1080p recommended
 */
@Service
public class VideoTranscodingService {
    
    private static final Logger log = LoggerFactory.getLogger(VideoTranscodingService.class);
    
    /**
     * Check if video needs transcoding for WeChat compatibility
     * Returns true if video uses unsupported codec or non-baseline H.264
     * 
     * IMPORTANT: Android WeChat is very strict - it requires H.264 baseline profile.
     * Even H.264 main/high profiles can fail on some Android devices.
     */
    public boolean needsTranscoding(File videoFile) {
        try {
            // Use ffprobe to check video codec AND profile
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,profile",
                "-of", "csv=p=0",
                videoFile.getAbsolutePath()
            );
            
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String codecInfo = reader.readLine();
            int exitCode = process.waitFor();
            
            if (exitCode != 0 || codecInfo == null) {
                log.warn("Could not determine video codec, will transcode to be safe");
                return true;
            }
            
            log.info("Video codec info: {}", codecInfo);
            
            String lowerCodec = codecInfo.toLowerCase();
            
            // Always transcode non-H.264 codecs
            if (lowerCodec.contains("hevc") || lowerCodec.contains("h265") || 
                lowerCodec.contains("vp9") || lowerCodec.contains("av1")) {
                log.info("Video uses unsupported codec ({}), needs transcoding", codecInfo);
                return true;
            }
            
            // For H.264, check if it's baseline profile
            // Android WeChat requires baseline profile for reliable playback
            if (lowerCodec.contains("h264")) {
                // If profile info is available, check if it's baseline
                if (lowerCodec.contains("baseline") || lowerCodec.contains("constrained baseline")) {
                    log.info("Video is H.264 baseline profile, no transcoding needed");
                    return false;
                }
                // Main, High, or unknown profile - transcode to baseline for Android compatibility
                log.info("Video is H.264 but not baseline profile ({}), transcoding for Android compatibility", codecInfo);
                return true;
            }
            
            // Unknown codec - transcode to be safe
            log.info("Unknown video codec ({}), transcoding to be safe", codecInfo);
            return true;
            
        } catch (Exception e) {
            log.error("Error checking video codec: {}", e.getMessage());
            // If we can't check, transcode to be safe
            return true;
        }
    }
    
    /**
     * Transcode video to WeChat compatible format
     * Uses H.264 baseline profile with AAC audio
     * 
     * @param inputFile Source video file
     * @param outputFile Destination file (will be created)
     * @return true if transcoding succeeded
     */
    public boolean transcodeForWeChat(File inputFile, File outputFile) {
        try {
            log.info("Starting video transcoding: {} -> {}", inputFile.getName(), outputFile.getName());
            
            // FFmpeg command for WeChat compatible output:
            // -c:v libx264 - Use H.264 codec
            // -profile:v baseline - Use baseline profile for max compatibility
            // -level 3.1 - Compatible with most devices
            // -pix_fmt yuv420p - Standard pixel format
            // -c:a aac - Use AAC audio codec
            // -movflags +faststart - Enable fast start for streaming
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", inputFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-profile:v", "baseline",
                "-level", "3.1",
                "-pix_fmt", "yuv420p",
                "-preset", "fast",
                "-crf", "23",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                outputFile.getAbsolutePath()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output for logging
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("frame=") || line.contains("error") || line.contains("Error")) {
                    log.debug("FFmpeg: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                log.info("✅ Video transcoding completed successfully. Output size: {} bytes", outputFile.length());
                return true;
            } else {
                log.error("❌ Video transcoding failed with exit code: {}", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Video transcoding error: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Transcode video if needed, otherwise return original file
     * 
     * @param inputFile Source video file
     * @return Transcoded file (new temp file) or original file if no transcoding needed
     */
    public File transcodeIfNeeded(File inputFile) {
        if (!needsTranscoding(inputFile)) {
            log.info("Video does not need transcoding, using original");
            return inputFile;
        }
        
        try {
            // Create temp file for transcoded output
            File outputFile = File.createTempFile("transcoded_", ".mp4");
            outputFile.deleteOnExit();
            
            if (transcodeForWeChat(inputFile, outputFile)) {
                return outputFile;
            } else {
                // Transcoding failed, return original
                log.warn("Transcoding failed, using original video");
                outputFile.delete();
                return inputFile;
            }
            
        } catch (Exception e) {
            log.error("Error creating temp file for transcoding: {}", e.getMessage());
            return inputFile;
        }
    }
}
