package com.example.demo.dao;

import com.example.demo.model.Invite;
import java.util.List;

public interface GroupDao {
    void save(Invite group);
    void update(Invite group);
    Invite findByToken(String token);
    Invite findById(String id);
    List<Invite> findByManagerId(String managerId);
    List<Invite> findByStatus(String status);
    void delete(String id);
    void updateStatus(String id, String status);
    
    // New method for getting user's group
    String getUserGroupId(String userId);
}
