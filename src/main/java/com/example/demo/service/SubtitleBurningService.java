package com.example.demo.service;

import com.example.demo.ai.subtitle.SubtitleSegment;
import com.example.demo.model.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Service for burning subtitles into videos using FFmpeg
 * 
 * Generates SRT files from subtitleSegments and applies them to videos
 */
@Service
public class SubtitleBurningService {
    
    private static final Logger log = LoggerFactory.getLogger(SubtitleBurningService.class);
    
    /**
     * Generate SRT file from scenes with subtitleSegments
     * 
     * @param scenes List of scenes with subtitleSegments
     * @return Path to generated SRT file
     */
    public String generateSrtFile(List<Scene> scenes) throws IOException {
        File srtFile = Files.createTempFile("subtitles_", ".srt").toFile();
        srtFile.deleteOnExit();
        
        int sequenceNumber = 1;
        
        try (FileWriter writer = new FileWriter(srtFile)) {
            for (Scene scene : scenes) {
                if (scene.getSubtitleSegments() == null || scene.getSubtitleSegments().isEmpty()) {
                    continue;
                }
                
                for (SubtitleSegment segment : scene.getSubtitleSegments()) {
                    writer.write(segment.toSRT(sequenceNumber));
                    writer.write("\n");
                    sequenceNumber++;
                }
            }
        }
        
        log.info("✅ Generated SRT file: {} with {} subtitle entries", 
            srtFile.getAbsolutePath(), sequenceNumber - 1);
        
        // Log SRT content for debugging
        try {
            String srtContent = new String(Files.readAllBytes(srtFile.toPath()));
            log.info("📝 SRT Content:\n{}", srtContent);
        } catch (Exception e) {
            log.warn("Could not read SRT file for logging: {}", e.getMessage());
        }
        
        return srtFile.getAbsolutePath();
    }
    
    /**
     * Build FFmpeg subtitle filter with custom styling
     * 
     * @param srtPath Path to SRT file
     * @param options Subtitle styling options
     * @return FFmpeg filter string
     */
    public String buildSubtitleFilter(String srtPath, SubtitleOptions options) {
        StringBuilder filter = new StringBuilder();
        filter.append("subtitles=").append(escapePath(srtPath));
        
        if (options != null) {
            filter.append(":force_style='");
            
            // Font name - FFmpeg force_style only accepts ONE font name (no commas!)
            // Use Noto Sans CJK SC which is confirmed to be installed in the container
            filter.append("FontName=Noto Sans CJK SC,");  // Exact match for installed font
            
            // Font size
            if (options.fontSize > 0) {
                filter.append("FontSize=").append(options.fontSize).append(",");
            }
            
            // Primary color (text color) in BGR format with alpha
            if (options.textColor != null) {
                filter.append("PrimaryColour=").append(convertColorToBGRA(options.textColor)).append(",");
            }
            
            // Outline color (border)
            if (options.outlineColor != null) {
                filter.append("OutlineColour=").append(convertColorToBGRA(options.outlineColor)).append(",");
            }
            
            // Outline width
            if (options.outlineWidth > 0) {
                filter.append("Outline=").append(options.outlineWidth).append(",");
            }
            
            // BorderStyle: 1 = normal text with outline, no box
            filter.append("BorderStyle=1,");
            
            // Bold
            if (options.bold) {
                filter.append("Bold=1,");
            }
            
            // Alignment (1=left, 2=center, 3=right, bottom row)
            filter.append("Alignment=").append(options.alignment);
            
            filter.append("'");
        }
        
        return filter.toString();
    }
    
    /**
     * Convert hex color to BGRA format for ASS subtitles
     * Format: &HAABBGGRR (alpha, blue, green, red)
     * 
     * @param hexColor Color in #RRGGBB or #RRGGBBAA format
     * @return BGRA string for ASS
     */
    private String convertColorToBGRA(String hexColor) {
        // Remove # if present
        hexColor = hexColor.replace("#", "");
        
        // Parse RGB
        int r, g, b, a = 255;
        
        if (hexColor.length() == 6) {
            r = Integer.parseInt(hexColor.substring(0, 2), 16);
            g = Integer.parseInt(hexColor.substring(2, 4), 16);
            b = Integer.parseInt(hexColor.substring(4, 6), 16);
        } else if (hexColor.length() == 8) {
            r = Integer.parseInt(hexColor.substring(0, 2), 16);
            g = Integer.parseInt(hexColor.substring(2, 4), 16);
            b = Integer.parseInt(hexColor.substring(4, 6), 16);
            a = Integer.parseInt(hexColor.substring(6, 8), 16);
        } else {
            // Default to white
            return "&H00FFFFFF";
        }
        
        // Convert to BGRA format: &HAABBGGRR
        // IMPORTANT: In ASS format, alpha is INVERTED: 00 = opaque, FF = transparent
        int assAlpha = 255 - a;  // Invert alpha for ASS format
        return String.format("&H%02X%02X%02X%02X", assAlpha, b, g, r);
    }
    
    /**
     * Escape file path for FFmpeg
     */
    private String escapePath(String path) {
        // Escape special characters for FFmpeg
        return path.replace("\\", "\\\\")
                   .replace(":", "\\:")
                   .replace("'", "\\'");
    }
    
    /**
     * Calculate subtitle alignment based on box positions and aspect ratio
     * For 16:9 landscape: right side = top (alignment 8), left side = bottom (alignment 2)
     * For 9:16 portrait: right side = right (alignment 3), left side = left (alignment 1)
     * 
     * @param scenes List of scenes with keyElementsWithBoxes
     * @param sourceAspect Video aspect ratio (e.g., "16:9" or "9:16")
     * @return Alignment value (1=bottom-left, 2=bottom-center, 3=bottom-right, 7=top-left, 8=top-center, 9=top-right)
     */
    public int calculateSubtitleAlignment(List<Scene> scenes, String sourceAspect) {
        boolean isLandscape = isLandscapeAspect(sourceAspect);
        
        // Calculate average X position of all boxes
        double totalX = 0;
        int boxCount = 0;
        
        for (Scene scene : scenes) {
            if (scene.getKeyElementsWithBoxes() != null) {
                for (var element : scene.getKeyElementsWithBoxes()) {
                    if (element.getBox() != null && element.getBox().length == 4) {
                        // Box format: [x, y, width, height] in normalized coordinates (0-1)
                        double x = element.getBox()[0];
                        double width = element.getBox()[2];
                        double centerX = x + width / 2;
                        totalX += centerX;
                        boxCount++;
                    }
                }
            }
        }
        
        if (boxCount == 0) {
            // No boxes found, use default bottom-center
            return 2;
        }
        
        double avgX = totalX / boxCount;
        
        if (isLandscape) {
            // For 16:9 landscape (phone held vertically, video rotated):
            // Right side (avgX > 0.5) becomes TOP when rotated → subtitles on LEFT (bottom when rotated) = alignment 2 (bottom-center)
            // Left side (avgX < 0.5) becomes BOTTOM when rotated → subtitles on RIGHT (top when rotated) = alignment 8 (top-center)
            if (avgX > 0.5) {
                log.info("📍 Boxes on RIGHT side (top when rotated) → Subtitles on LEFT/BOTTOM: alignment=2");
                return 2;  // Bottom-center
            } else {
                log.info("📍 Boxes on LEFT side (bottom when rotated) → Subtitles on RIGHT/TOP: alignment=8");
                return 8;  // Top-center
            }
        } else {
            // For 9:16 portrait:
            // Right side (avgX > 0.5) → subtitles on left = alignment 1 (bottom-left)
            // Left side (avgX < 0.5) → subtitles on right = alignment 3 (bottom-right)
            if (avgX > 0.5) {
                log.info("📍 Boxes on RIGHT side → Subtitles on LEFT: alignment=1");
                return 1;  // Bottom-left
            } else {
                log.info("📍 Boxes on LEFT side → Subtitles on RIGHT: alignment=3");
                return 3;  // Bottom-right
            }
        }
    }
    
    /**
     * Check if aspect ratio is landscape (width > height)
     */
    private boolean isLandscapeAspect(String sourceAspect) {
        if (sourceAspect == null || sourceAspect.isEmpty()) {
            return false;
        }
        
        String[] parts = sourceAspect.split(":");
        if (parts.length != 2) {
            return false;
        }
        
        try {
            double width = Double.parseDouble(parts[0].trim());
            double height = Double.parseDouble(parts[1].trim());
            return width > height;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Subtitle styling options
     */
    public static class SubtitleOptions {
        public int fontSize = 32;  // Increased from 24 for better visibility
        public String textColor = "#FFFFFF";  // White
        public String outlineColor = "#000000";  // Black
        public int outlineWidth = 3;  // Increased from 2 for stronger outline
        public String backgroundColor = "#000000C0";  // Semi-transparent black box (75% opacity)
        public boolean bold = true;  // Changed to true for better visibility
        public int alignment = 2;  // 2 = bottom center (default)
        
        // Preset styles
        public static SubtitleOptions defaultStyle() {
            return new SubtitleOptions();
        }
        
        public static SubtitleOptions whiteOnBlack() {
            SubtitleOptions opts = new SubtitleOptions();
            opts.textColor = "#FFFFFF";
            opts.outlineColor = "#000000";
            opts.outlineWidth = 3;
            opts.bold = true;
            return opts;
        }
        
        public static SubtitleOptions yellowOnBlack() {
            SubtitleOptions opts = new SubtitleOptions();
            opts.textColor = "#FFFF00";
            opts.outlineColor = "#000000";
            opts.outlineWidth = 3;
            opts.bold = true;
            return opts;
        }
        
        public static SubtitleOptions blackOnWhite() {
            SubtitleOptions opts = new SubtitleOptions();
            opts.textColor = "#000000";
            opts.backgroundColor = "#FFFFFF80";  // Semi-transparent white box
            opts.outlineWidth = 0;
            return opts;
        }
    }
}
