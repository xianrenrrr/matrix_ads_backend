package com.example.demo.dao;

import com.example.demo.model.Group;
import java.util.List;

public interface GroupDao {
    void save(Group group);
    void update(Group group);
    Group findByToken(String token);
    Group findById(String id);
    List<Group> findByManagerId(String managerId);
    List<Group> findByStatus(String status);
    void delete(String id);
    void updateStatus(String id, String status);
    
    // Get user's group ID
    String getUserGroupId(String userId);
    
    // Template-group relationship methods
    void addTemplateToGroup(String groupId, String templateId);
    void removeTemplateFromGroup(String groupId, String templateId);
}
