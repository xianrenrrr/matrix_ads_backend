package com.example.demo.service;

import java.util.List;

/**
 * Simple video compilation service
 */
public interface VideoCompilationService {
    
    /**
     * Compile approved scenes into final video
     * @param templateId Template ID
     * @param userId User ID
     * @param compiledBy Manager who triggered compilation
     * @return URL of compiled video
     */
    String compileVideo(String templateId, String userId, String compiledBy);
    
    /**
     * Compile approved scenes into final video with background music
     * @param templateId Template ID
     * @param userId User ID
     * @param compiledBy Manager who triggered compilation
     * @param bgmUrls List of background music URLs (will loop if video is longer)
     * @param bgmVolume Volume level for BGM (0.0 to 1.0)
     * @return URL of compiled video
     */
    String compileVideoWithBGM(String templateId, String userId, String compiledBy, List<String> bgmUrls, double bgmVolume);
    
    /**
     * Compile approved scenes into final video with subtitles
     * @param templateId Template ID
     * @param userId User ID
     * @param compiledBy Manager who triggered compilation
     * @param subtitleOptions Subtitle styling options (null for no subtitles)
     * @return URL of compiled video
     */
    String compileVideoWithSubtitles(String templateId, String userId, String compiledBy, SubtitleBurningService.SubtitleOptions subtitleOptions);
    
    /**
     * Compile approved scenes into final video with BGM and subtitles
     * @param templateId Template ID
     * @param userId User ID
     * @param compiledBy Manager who triggered compilation
     * @param bgmUrls List of background music URLs (will loop if video is longer)
     * @param bgmVolume Volume level for BGM (0.0 to 1.0)
     * @param subtitleOptions Subtitle styling options (null for no subtitles)
     * @return URL of compiled video
     */
    String compileVideoWithBGMAndSubtitles(String templateId, String userId, String compiledBy, List<String> bgmUrls, double bgmVolume, SubtitleBurningService.SubtitleOptions subtitleOptions);
}