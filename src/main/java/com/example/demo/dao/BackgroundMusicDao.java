package com.example.demo.dao;

import com.example.demo.model.BackgroundMusic;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface BackgroundMusicDao {
    String saveBackgroundMusic(BackgroundMusic bgm) throws ExecutionException, InterruptedException;
    BackgroundMusic getBackgroundMusic(String id) throws ExecutionException, InterruptedException;
    List<BackgroundMusic> getBackgroundMusicByUserId(String userId) throws ExecutionException, InterruptedException;
    boolean deleteBackgroundMusic(String id) throws ExecutionException, InterruptedException;
}
