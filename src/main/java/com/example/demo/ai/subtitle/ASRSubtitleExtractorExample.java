package com.example.demo.ai.subtitle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Example usage of ASRSubtitleExtractor
 * 
 * This shows how to use the ASR service to extract subtitles from videos
 */
@Component
public class ASRSubtitleExtractorExample {
    
    @Autowired
    private ASRSubtitleExtractor asrExtractor;
    
    /**
     * Example 1: Extract subtitles from a video URL
     */
    public void extractFromUrl() {
        String videoUrl = "https://storage.googleapis.com/your-bucket/video.mp4";
        String language = "zh"; // Chinese
        
        List<SubtitleSegment> segments = asrExtractor.extract(videoUrl, language);
        
        // Print results
        System.out.println("Found " + segments.size() + " subtitle segments:");
        for (SubtitleSegment segment : segments) {
            System.out.println(segment);
        }
    }
    
    /**
     * Example 2: Extract subtitles from local file
     */
    public void extractFromLocalFile() {
        String videoPath = "/tmp/video.mp4";
        String language = "en"; // English
        
        List<SubtitleSegment> segments = asrExtractor.extract(videoPath, language);
        
        // Convert to SRT format
        StringBuilder srt = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            srt.append(segments.get(i).toSRT(i + 1));
            srt.append("\n");
        }
        
        System.out.println("SRT Format:");
        System.out.println(srt.toString());
    }
    
    /**
     * Example 3: Use in video compilation
     */
    public void useInVideoCompilation() {
        String videoUrl = "https://storage.googleapis.com/your-bucket/ad-video.mp4";
        
        // Extract subtitles
        List<SubtitleSegment> segments = asrExtractor.extract(videoUrl, "zh");
        
        // Now you can use these segments to:
        // 1. Burn subtitles into video using FFmpeg
        // 2. Display KTV-style subtitles
        // 3. Analyze speech timing
        // 4. Generate transcript
        
        for (SubtitleSegment segment : segments) {
            System.out.printf("%.2fs - %.2fs: %s%n", 
                segment.getStartTimeMs() / 1000.0,
                segment.getEndTimeMs() / 1000.0,
                segment.getText()
            );
        }
    }
}
