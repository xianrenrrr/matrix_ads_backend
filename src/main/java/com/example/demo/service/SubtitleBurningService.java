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
            
            // Font name - use fonts that support Chinese characters
            // Try multiple fallbacks in order: Noto Sans CJK > DejaVu Sans > Sans
            filter.append("FontName=Noto Sans CJK SC,DejaVu Sans,Sans,");  // Multiple fallbacks for Chinese support
            
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
            
            // Background color (box behind text)
            if (options.backgroundColor != null) {
                filter.append("BackColour=").append(convertColorToBGRA(options.backgroundColor)).append(",");
                filter.append("BorderStyle=4,");  // 4 = box background
            }
            
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
        return String.format("&H%02X%02X%02X%02X", a, b, g, r);
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
