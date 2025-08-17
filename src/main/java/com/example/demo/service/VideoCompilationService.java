package com.example.demo.service;

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
}