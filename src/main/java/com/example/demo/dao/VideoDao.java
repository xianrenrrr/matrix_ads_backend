package com.example.demo.dao;

import com.example.demo.model.Video;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface VideoDao {
    Video saveVideo(Video video) throws ExecutionException, InterruptedException;
    List<Video> getVideosByUserId(String userId) throws ExecutionException, InterruptedException;
    Video getVideoById(String videoId) throws ExecutionException, InterruptedException;
    void updateVideo(Video video) throws ExecutionException, InterruptedException;
    Video saveVideoWithTemplate(Video video, String templateId) throws ExecutionException, InterruptedException;
}
