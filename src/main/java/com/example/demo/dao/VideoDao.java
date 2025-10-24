package com.example.demo.dao;

import com.example.demo.model.Video;
import java.util.concurrent.ExecutionException;

public interface VideoDao {
    Video saveVideo(Video video) throws ExecutionException, InterruptedException;
    Video getVideoById(String videoId) throws ExecutionException, InterruptedException;
    void updateVideo(Video video) throws ExecutionException, InterruptedException;
    Video saveVideoWithTemplate(Video video, String templateId) throws ExecutionException, InterruptedException;
    boolean deleteVideoById(String videoId) throws ExecutionException, InterruptedException;
    
    // Storage operations
    Video uploadAndSaveVideo(org.springframework.web.multipart.MultipartFile file, String userId, String videoId) throws Exception;
    String getSignedUrl(String videoUrl) throws Exception;
}
