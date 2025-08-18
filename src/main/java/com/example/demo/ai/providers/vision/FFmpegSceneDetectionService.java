package com.example.demo.ai.providers.vision;

import com.example.demo.model.SceneSegment;
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
    
    @Value("${ai.scenes.threshold:0.35}")
    private double sceneThreshold;
    
    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;
    
    /**
     * Detects scene cuts using FFmpeg scene change filter
     * @param videoUrl URL or path to the video file
     * @return List of scene segments with start/end timestamps
     */
    public List<SceneSegment> detectScenes(String videoUrl) {
        System.out.printf("FFmpeg scene detection starting for: %s with threshold: %.2f%n", videoUrl, sceneThreshold);
        List<SceneSegment> scenes = new ArrayList<>();
        
        try {
            // Build FFmpeg command for scene detection
            String[] command = {
                ffmpegPath,
                "-i", videoUrl,
                "-filter:v", String.format("select='gt(scene,%.2f)',showinfo", sceneThreshold),
                "-f", "null",
                "-"
            };
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Parse FFmpeg output for scene timestamps
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Pattern timePattern = Pattern.compile("pts_time:([\\d.]+)");
                List<Double> sceneTimestamps = new ArrayList<>();
                sceneTimestamps.add(0.0); // Start of video
                
                while ((line = reader.readLine()) != null) {
                    if (line.contains("pts_time:")) {
                        Matcher matcher = timePattern.matcher(line);
                        if (matcher.find()) {
                            double timestamp = Double.parseDouble(matcher.group(1));
                            sceneTimestamps.add(timestamp);
                            System.out.printf("Scene cut detected at: %.2f seconds%n", timestamp);
                        }
                    }
                }
                
                // Wait for process to complete
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.printf("FFmpeg process exited with code: %d%n", exitCode);
                }
                
                // Create scene segments from timestamps
                for (int i = 0; i < sceneTimestamps.size() - 1; i++) {
                    SceneSegment segment = new SceneSegment();
                    segment.setStartTime(Duration.ofMillis((long)(sceneTimestamps.get(i) * 1000)));
                    segment.setEndTime(Duration.ofMillis((long)(sceneTimestamps.get(i + 1) * 1000)));
                    scenes.add(segment);
                }
                
                // Add final scene to end of video (estimate 30 seconds if no end detected)
                if (!sceneTimestamps.isEmpty() && sceneTimestamps.size() > 1) {
                    SceneSegment lastSegment = new SceneSegment();
                    lastSegment.setStartTime(Duration.ofMillis((long)(sceneTimestamps.get(sceneTimestamps.size() - 1) * 1000)));
                    // Get video duration or use a reasonable default
                    lastSegment.setEndTime(Duration.ofMillis((long)((sceneTimestamps.get(sceneTimestamps.size() - 1) + 30) * 1000)));
                    scenes.add(lastSegment);
                }
            }
            
            System.out.printf("FFmpeg scene detection completed: %d scenes detected%n", scenes.size());
            
            // If no scenes detected, create single scene for entire video
            if (scenes.isEmpty()) {
                SceneSegment fullVideo = new SceneSegment();
                fullVideo.setStartTime(Duration.ZERO);
                fullVideo.setEndTime(Duration.ofSeconds(30)); // Default duration
                scenes.add(fullVideo);
                System.out.println("No scene cuts detected, treating as single scene");
            }
            
        } catch (Exception e) {
            System.err.printf("Error in FFmpeg scene detection: %s%n", e.getMessage());
            e.printStackTrace();
            
            // Fallback: single scene
            SceneSegment fallback = new SceneSegment();
            fallback.setStartTime(Duration.ZERO);
            fallback.setEndTime(Duration.ofSeconds(30));
            scenes.add(fallback);
        }
        
        return scenes;
    }
}