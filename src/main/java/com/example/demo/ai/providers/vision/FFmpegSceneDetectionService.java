package com.example.demo.ai.providers.vision;

import com.example.demo.ai.shared.GcsFileResolver;
import com.example.demo.model.SceneSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FFmpeg-based scene detection service for Chinese-first AI workflow
 * Replaces Google Video Intelligence API with local FFmpeg processing
 */
@Service
public class FFmpegSceneDetectionService {
    
    @Autowired
    private GcsFileResolver gcsFileResolver;
    
    @Value("${ai.scenes.threshold:0.35}")
    private double sceneThreshold;
    
    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;
    
    @Value("${ffprobe.path:ffprobe}")
    private String ffprobePath;
    
    /**
     * Detects scene cuts using FFmpeg scene change filter
     * @param videoUrl URL or path to the video file  
     * @return List of scene segments with start/end timestamps
     */
    public List<SceneSegment> detectScenes(String videoUrl) {
        System.out.printf("FFmpeg scene detection starting for: %s with threshold: %.2f%n", videoUrl, sceneThreshold);
        List<SceneSegment> scenes = new ArrayList<>();
        
        try {
            // Resolve GCS URL to local file to avoid 403 errors
            try (GcsFileResolver.ResolvedFile resolvedFile = gcsFileResolver.resolve(videoUrl)) {
                String localPath = resolvedFile.getPathAsString();
                
                // Get real video duration using ffprobe
                Double videoDuration = getVideoDuration(localPath);
                
                // Detect scene change timestamps using FFmpeg
                List<Double> sceneTimestamps = detectSceneTimestamps(localPath);
                
                // Create scene segments from timestamps
                scenes = createSceneSegments(sceneTimestamps, videoDuration);
                
                System.out.printf("FFmpeg scene detection completed: %d scenes detected%n", scenes.size());
            }
            
        } catch (Exception e) {
            System.err.printf("Error in FFmpeg scene detection: %s%n", e.getMessage());
            e.printStackTrace();
            
            // Fallback: single scene with default duration
            scenes = createFallbackScene();
        }
        
        return scenes;
    }
    
    /**
     * Get video duration using ffprobe
     */
    private Double getVideoDuration(String localPath) {
        try {
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
                    System.out.printf("Video duration: %.3f seconds%n", duration);
                    return duration;
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.printf("ffprobe exited with code: %d%n", exitCode);
            }
            
        } catch (Exception e) {
            System.err.printf("Failed to get video duration: %s%n", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Detect scene change timestamps using FFmpeg
     */
    private List<Double> detectSceneTimestamps(String localPath) throws Exception {
        List<Double> sceneTimestamps = new ArrayList<>();
        sceneTimestamps.add(0.0); // Always start with 0
        
        // Build FFmpeg command (no shell quotes needed in ProcessBuilder)
        String[] command = {
            ffmpegPath,
            "-i", localPath,
            "-vf", String.format("select=gt(scene,%.2f),showinfo", sceneThreshold),
            "-f", "null",
            "-"
        };
        
        System.out.printf("Running FFmpeg command: %s%n", String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        
        // Parse stderr for showinfo output (showinfo writes to stderr)
        Pattern timePattern = Pattern.compile("pts_time:([\\d.]+)");
        StringBuilder errorOutput = new StringBuilder();
        
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
                if (line.contains("pts_time:")) {
                    Matcher matcher = timePattern.matcher(line);
                    if (matcher.find()) {
                        double timestamp = Double.parseDouble(matcher.group(1));
                        sceneTimestamps.add(timestamp);
                        System.out.printf("Scene cut detected at: %.3f seconds%n", timestamp);
                    }
                }
            }
        }
        
        // Wait for process completion
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.out.printf("FFmpeg scene detection exited with code: %d%n", exitCode);
            System.out.printf("FFmpeg error output:%n%s%n", errorOutput.toString());
        }
        
        return sceneTimestamps;
    }
    
    /**
     * Create scene segments from timestamps and video duration
     */
    private List<SceneSegment> createSceneSegments(List<Double> sceneTimestamps, Double videoDuration) {
        List<SceneSegment> scenes = new ArrayList<>();
        
        // Create segments between consecutive timestamps
        for (int i = 0; i < sceneTimestamps.size() - 1; i++) {
            SceneSegment segment = new SceneSegment();
            segment.setStartTime(Duration.ofMillis((long)(sceneTimestamps.get(i) * 1000)));
            segment.setEndTime(Duration.ofMillis((long)(sceneTimestamps.get(i + 1) * 1000)));
            scenes.add(segment);
        }
        
        // Add final scene that ends at real video duration
        if (!sceneTimestamps.isEmpty()) {
            SceneSegment lastSegment = new SceneSegment();
            lastSegment.setStartTime(Duration.ofMillis((long)(sceneTimestamps.get(sceneTimestamps.size() - 1) * 1000)));
            
            // Use real duration from ffprobe, fallback to estimated duration
            double endTime = videoDuration != null ? videoDuration : (sceneTimestamps.get(sceneTimestamps.size() - 1) + 30);
            lastSegment.setEndTime(Duration.ofMillis((long)(endTime * 1000)));
            scenes.add(lastSegment);
        }
        
        // If no scenes detected, create single scene for entire video
        if (scenes.isEmpty()) {
            SceneSegment fullVideo = new SceneSegment();
            fullVideo.setStartTime(Duration.ZERO);
            fullVideo.setEndTime(Duration.ofMillis((long)((videoDuration != null ? videoDuration : 30) * 1000)));
            scenes.add(fullVideo);
            System.out.println("No scene cuts detected, treating as single scene");
        }
        
        return scenes;
    }
    
    /**
     * Create fallback scene when detection fails
     */
    private List<SceneSegment> createFallbackScene() {
        List<SceneSegment> scenes = new ArrayList<>();
        SceneSegment fallback = new SceneSegment();
        fallback.setStartTime(Duration.ZERO);
        fallback.setEndTime(Duration.ofSeconds(30)); // Default 30 seconds
        scenes.add(fallback);
        return scenes;
    }
}