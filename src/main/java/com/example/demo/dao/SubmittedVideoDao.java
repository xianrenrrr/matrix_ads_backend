package com.example.demo.dao;

import com.example.demo.model.SubmittedVideo;
import java.util.List;

public interface SubmittedVideoDao {
    SubmittedVideo findById(String compositeVideoId);
    void update(SubmittedVideo video);
    List<SubmittedVideo> findByAssignmentIds(List<String> assignmentIds);
    List<SubmittedVideo> findByUserId(String userId);
}
