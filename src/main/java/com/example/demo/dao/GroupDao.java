package com.example.demo.dao;

import com.example.demo.model.Group;
import java.util.List;

public interface GroupDao {
    void save(Group group);
    Group findById(String id);
    Group findByNameAndManagerId(String groupName, String managerId);
    List<Group> findByManagerId(String managerId);
    List<Group> findGroupsByMemberId(String memberId);
    void update(Group group);
    void delete(String id);
    void addMemberToGroup(String groupId, String memberId);
    void removeMemberFromGroup(String groupId, String memberId);
}