package com.example.demo.ai.subtitle;

/**
 * Represents a single subtitle segment with timing information
 * Used for KTV-style subtitle display in compiled videos
 * 
 * Example:
 * - startTimeMs: 100 (0.1 seconds)
 * - endTimeMs: 3820 (3.82 seconds)
 * - text: "Hello world, 这里是阿里巴巴语音实验室。"
 * - confidence: 1.0
 */
public class SubtitleSegment {
    private long startTimeMs;  // Start time in milliseconds
    private long endTimeMs;    // End time in milliseconds
    private String text;       // Subtitle text
    private double confidence; // Recognition confidence (0.0-1.0)
    
    // Position data (for OCR filtering)
    private Integer top;       // Y position (pixels from top)
    private Integer left;      // X position (pixels from left)
    private Integer width;     // Width in pixels
    private Integer height;    // Height in pixels
    
    public SubtitleSegment() {}
    
    public SubtitleSegment(long startTimeMs, long endTimeMs, String text) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.text = text;
        this.confidence = 1.0;
    }
    
    public SubtitleSegment(long startTimeMs, long endTimeMs, String text, double confidence) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.text = text;
        this.confidence = confidence;
    }
    
    public long getStartTimeMs() {
        return startTimeMs;
    }
    
    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }
    
    public long getEndTimeMs() {
        return endTimeMs;
    }
    
    public void setEndTimeMs(long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public Integer getTop() {
        return top;
    }
    
    public void setTop(Integer top) {
        this.top = top;
    }
    
    public Integer getLeft() {
        return left;
    }
    
    public void setLeft(Integer left) {
        this.left = left;
    }
    
    public Integer getWidth() {
        return width;
    }
    
    public void setWidth(Integer width) {
        this.width = width;
    }
    
    public Integer getHeight() {
        return height;
    }
    
    public void setHeight(Integer height) {
        this.height = height;
    }
    
    /**
     * Get duration in milliseconds
     */
    public long getDurationMs() {
        return endTimeMs - startTimeMs;
    }
    
    /**
     * Get duration in seconds
     */
    public double getDurationSeconds() {
        return getDurationMs() / 1000.0;
    }
    
    /**
     * Convert to SRT format (SubRip subtitle format)
     * Used for burning subtitles into video with FFmpeg
     * 
     * Example output:
     * 1
     * 00:00:00,100 --> 00:00:03,820
     * Hello world, 这里是阿里巴巴语音实验室。
     */
    public String toSRT(int sequenceNumber) {
        return String.format("%d\n%s --> %s\n%s\n",
            sequenceNumber,
            formatSRTTime(startTimeMs),
            formatSRTTime(endTimeMs),
            text
        );
    }
    
    /**
     * Format milliseconds to SRT time format: HH:MM:SS,mmm
     */
    private String formatSRTTime(long ms) {
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }
    
    @Override
    public String toString() {
        return String.format("SubtitleSegment[%dms-%dms: %s (conf=%.2f)]", 
            startTimeMs, endTimeMs, text, confidence);
    }
}
