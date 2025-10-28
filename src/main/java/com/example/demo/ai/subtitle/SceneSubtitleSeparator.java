package com.example.demo.ai.subtitle;

import com.example.demo.model.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to separate subtitles by scene based on timing
 * 
 * Given:
 * - List of scenes with startTimeMs and endTimeMs
 * - List of subtitle segments with startTimeMs and endTimeMs
 * 
 * Assigns each subtitle segment to the appropriate scene(s)
 */
@Service
public class SceneSubtitleSeparator {
    private static final Logger log = LoggerFactory.getLogger(SceneSubtitleSeparator.class);
    
    /**
     * Separate subtitles by scene
     * 
     * @param scenes List of scenes with timing information
     * @param allSubtitles All subtitle segments from the video
     * @return Updated scenes with subtitles assigned
     */
    public List<Scene> separateSubtitlesByScene(List<Scene> scenes, List<SubtitleSegment> allSubtitles) {
        log.info("Separating {} subtitles into {} scenes", allSubtitles.size(), scenes.size());
        
        if (scenes == null || scenes.isEmpty()) {
            log.warn("No scenes provided");
            return scenes;
        }
        
        if (allSubtitles == null || allSubtitles.isEmpty()) {
            log.warn("No subtitles provided");
            return scenes;
        }
        
        // Initialize subtitle lists for each scene
        for (Scene scene : scenes) {
            scene.setSubtitles(new ArrayList<>());
        }
        
        // Assign each subtitle to appropriate scene(s)
        for (SubtitleSegment subtitle : allSubtitles) {
            boolean assigned = false;
            
            for (Scene scene : scenes) {
                if (subtitleBelongsToScene(subtitle, scene)) {
                    scene.getSubtitles().add(subtitle);
                    assigned = true;
                    log.debug("Assigned subtitle '{}' ({}-{}ms) to scene {} ({}-{}ms)",
                        subtitle.getText().substring(0, Math.min(20, subtitle.getText().length())),
                        subtitle.getStartTimeMs(),
                        subtitle.getEndTimeMs(),
                        scene.getSceneNumber(),
                        scene.getStartTimeMs(),
                        scene.getEndTimeMs());
                }
            }
            
            if (!assigned) {
                log.warn("Subtitle '{}' ({}-{}ms) not assigned to any scene",
                    subtitle.getText().substring(0, Math.min(20, subtitle.getText().length())),
                    subtitle.getStartTimeMs(),
                    subtitle.getEndTimeMs());
            }
        }
        
        // Log summary
        for (Scene scene : scenes) {
            log.info("Scene {}: {} subtitles ({}-{}ms)",
                scene.getSceneNumber(),
                scene.getSubtitles().size(),
                scene.getStartTimeMs(),
                scene.getEndTimeMs());
        }
        
        return scenes;
    }
    
    /**
     * Check if a subtitle belongs to a scene
     * 
     * A subtitle belongs to a scene if:
     * - Its start time is within the scene's time range, OR
     * - Its end time is within the scene's time range, OR
     * - It spans across the entire scene
     * 
     * @param subtitle Subtitle segment
     * @param scene Scene with timing
     * @return true if subtitle belongs to scene
     */
    private boolean subtitleBelongsToScene(SubtitleSegment subtitle, Scene scene) {
        if (scene.getStartTimeMs() == null || scene.getEndTimeMs() == null) {
            log.warn("Scene {} has no timing information", scene.getSceneNumber());
            return false;
        }
        
        long sceneStart = scene.getStartTimeMs();
        long sceneEnd = scene.getEndTimeMs();
        long subStart = subtitle.getStartTimeMs();
        long subEnd = subtitle.getEndTimeMs();
        
        // Check if subtitle overlaps with scene
        // Overlap occurs if: subtitle starts before scene ends AND subtitle ends after scene starts
        boolean overlaps = subStart < sceneEnd && subEnd > sceneStart;
        
        return overlaps;
    }
    
    /**
     * Adjust subtitle timestamps to be relative to scene start
     * 
     * Useful for displaying subtitles in scene-specific video players
     * 
     * @param scene Scene with subtitles
     * @return Scene with adjusted subtitle timestamps
     */
    public Scene adjustSubtitlesToSceneTime(Scene scene) {
        if (scene.getSubtitles() == null || scene.getSubtitles().isEmpty()) {
            return scene;
        }
        
        if (scene.getStartTimeMs() == null) {
            log.warn("Scene {} has no start time, cannot adjust subtitles", scene.getSceneNumber());
            return scene;
        }
        
        long sceneStart = scene.getStartTimeMs();
        
        log.info("Adjusting {} subtitles in scene {} to be relative to scene start ({}ms)",
            scene.getSubtitles().size(), scene.getSceneNumber(), sceneStart);
        
        List<SubtitleSegment> adjustedSubtitles = new ArrayList<>();
        
        for (SubtitleSegment subtitle : scene.getSubtitles()) {
            SubtitleSegment adjusted = new SubtitleSegment(
                Math.max(0, subtitle.getStartTimeMs() - sceneStart),
                subtitle.getEndTimeMs() - sceneStart,
                subtitle.getText(),
                subtitle.getConfidence()
            );
            adjustedSubtitles.add(adjusted);
            
            log.debug("Adjusted subtitle: {}ms-{}ms -> {}ms-{}ms",
                subtitle.getStartTimeMs(),
                subtitle.getEndTimeMs(),
                adjusted.getStartTimeMs(),
                adjusted.getEndTimeMs());
        }
        
        scene.setSubtitles(adjustedSubtitles);
        return scene;
    }
    
    /**
     * Get subtitles for a specific time range
     * 
     * @param allSubtitles All subtitle segments
     * @param startTimeMs Start time in milliseconds
     * @param endTimeMs End time in milliseconds
     * @return Subtitles within the time range
     */
    public List<SubtitleSegment> getSubtitlesInRange(
        List<SubtitleSegment> allSubtitles,
        long startTimeMs,
        long endTimeMs
    ) {
        List<SubtitleSegment> result = new ArrayList<>();
        
        for (SubtitleSegment subtitle : allSubtitles) {
            if (subtitle.getStartTimeMs() < endTimeMs && subtitle.getEndTimeMs() > startTimeMs) {
                result.add(subtitle);
            }
        }
        
        return result;
    }
    
    /**
     * Merge consecutive subtitle segments with the same text
     * Useful for cleaning up word-level subtitles
     * 
     * @param subtitles List of subtitle segments
     * @return Merged subtitle segments
     */
    public List<SubtitleSegment> mergeConsecutiveSubtitles(List<SubtitleSegment> subtitles) {
        if (subtitles == null || subtitles.size() <= 1) {
            return subtitles;
        }
        
        List<SubtitleSegment> merged = new ArrayList<>();
        SubtitleSegment current = null;
        
        for (SubtitleSegment subtitle : subtitles) {
            if (current == null) {
                current = new SubtitleSegment(
                    subtitle.getStartTimeMs(),
                    subtitle.getEndTimeMs(),
                    subtitle.getText(),
                    subtitle.getConfidence()
                );
            } else if (shouldMerge(current, subtitle)) {
                // Merge with current
                current.setEndTimeMs(subtitle.getEndTimeMs());
                current.setText(current.getText() + subtitle.getText());
                current.setConfidence(Math.min(current.getConfidence(), subtitle.getConfidence()));
            } else {
                // Save current and start new
                merged.add(current);
                current = new SubtitleSegment(
                    subtitle.getStartTimeMs(),
                    subtitle.getEndTimeMs(),
                    subtitle.getText(),
                    subtitle.getConfidence()
                );
            }
        }
        
        // Add last segment
        if (current != null) {
            merged.add(current);
        }
        
        log.info("Merged {} subtitles into {} segments", subtitles.size(), merged.size());
        return merged;
    }
    
    /**
     * Check if two consecutive subtitles should be merged
     * 
     * Merge if:
     * - Time gap is less than 100ms
     * - Both are single characters (Chinese/Japanese)
     * 
     * @param current Current subtitle
     * @param next Next subtitle
     * @return true if should merge
     */
    private boolean shouldMerge(SubtitleSegment current, SubtitleSegment next) {
        long gap = next.getStartTimeMs() - current.getEndTimeMs();
        
        // Don't merge if gap is too large
        if (gap > 100) {
            return false;
        }
        
        // Merge single characters (useful for Chinese/Japanese)
        if (current.getText().length() == 1 && next.getText().length() == 1) {
            return true;
        }
        
        return false;
    }
}
