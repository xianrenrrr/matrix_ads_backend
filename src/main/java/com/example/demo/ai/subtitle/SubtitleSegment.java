package com.example.demo.ai.subtitle;

/**
 * Represents a single subtitle segment with timing information
 */
public class SubtitleSegment {
    private long startTimeMs;  // Start time in milliseconds
    private long endTimeMs;    // End time in milliseconds
    private String text;       // Subtitle text
    
    public SubtitleSegment() {}
    
    public SubtitleSegment(long startTimeMs, long endTimeMs, String text) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.text = text;
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
    
    @Override
    public String toString() {
        return String.format("SubtitleSegment[%dms-%dms: %s]", startTimeMs, endTimeMs, text);
    }
}
