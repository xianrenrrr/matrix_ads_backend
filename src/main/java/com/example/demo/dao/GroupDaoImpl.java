package com.example.demo.dao;

import com.example.demo.model.Group;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Repository
public class GroupDaoImpl implements GroupDao {

    @Autowired
    private Firestore db;

    private static final String COLLECTION_NAME = "groups";

    @Override
    public void save(Group group) {
        try {
            if (group.getId() == null) {
                group.setId(UUID.randomUUID().toString());
            }
            group.setUpdatedAt(new Date());
            
            Map<String, Object> groupData = convertToMap(group);
            db.collection(COLLECTION_NAME).document(group.getId()).set(groupData).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error saving group: " + e.getMessage(), e);
        }
    }

    @Override
    public Group findById(String id) {
        try {
            DocumentSnapshot document = db.collection(COLLECTION_NAME).document(id).get().get();
            if (document.exists()) {
                return convertToGroup(document.getId(), document.getData());
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding group by ID: " + e.getMessage(), e);
        }
    }

    @Override
    public Group findByNameAndManagerId(String groupName, String managerId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("groupName", groupName)
                    .whereEqualTo("managerId", managerId)
                    .get().get();
            
            for (QueryDocumentSnapshot document : querySnapshot) {
                return convertToGroup(document.getId(), document.getData());
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding group by name and manager: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Group> findByManagerId(String managerId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                    .whereEqualTo("managerId", managerId)
                    .whereEqualTo("active", true)
                    .get().get();
            
            List<Group> groups = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot) {
                groups.add(convertToGroup(document.getId(), document.getData()));
            }
            return groups;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding groups by manager: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Group> findGroupsByMemberId(String memberId) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME)
                    .whereArrayContains("memberIds", memberId)
                    .whereEqualTo("active", true)
                    .get().get();
            
            List<Group> groups = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot) {
                groups.add(convertToGroup(document.getId(), document.getData()));
            }
            return groups;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error finding groups by member: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Group group) {
        try {
            group.setUpdatedAt(new Date());
            Map<String, Object> groupData = convertToMap(group);
            db.collection(COLLECTION_NAME).document(group.getId()).set(groupData).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error updating group: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String id) {
        try {
            db.collection(COLLECTION_NAME).document(id).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error deleting group: " + e.getMessage(), e);
        }
    }

    @Override
    public void addMemberToGroup(String groupId, String memberId) {
        try {
            DocumentReference groupRef = db.collection(COLLECTION_NAME).document(groupId);
            groupRef.update("memberIds", FieldValue.arrayUnion(memberId), 
                           "updatedAt", new Date()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error adding member to group: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeMemberFromGroup(String groupId, String memberId) {
        try {
            DocumentReference groupRef = db.collection(COLLECTION_NAME).document(groupId);
            groupRef.update("memberIds", FieldValue.arrayRemove(memberId), 
                           "updatedAt", new Date()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error removing member from group: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> convertToMap(Group group) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupName", group.getGroupName());
        data.put("managerId", group.getManagerId());
        data.put("managerName", group.getManagerName());
        data.put("memberIds", group.getMemberIds() != null ? group.getMemberIds() : new ArrayList<>());
        data.put("description", group.getDescription());
        data.put("createdAt", group.getCreatedAt());
        data.put("updatedAt", group.getUpdatedAt());
        data.put("active", group.isActive());
        return data;
    }

    @SuppressWarnings("unchecked")
    private Group convertToGroup(String id, Map<String, Object> data) {
        Group group = new Group();
        group.setId(id);
        group.setGroupName((String) data.get("groupName"));
        group.setManagerId((String) data.get("managerId"));
        group.setManagerName((String) data.get("managerName"));
        
        Object memberIdsObj = data.get("memberIds");
        if (memberIdsObj instanceof List) {
            group.setMemberIds((List<String>) memberIdsObj);
        } else {
            group.setMemberIds(new ArrayList<>());
        }
        
        group.setDescription((String) data.get("description"));
        group.setCreatedAt(data.get("createdAt") instanceof Date ? 
                          (Date) data.get("createdAt") : new Date());
        group.setUpdatedAt(data.get("updatedAt") instanceof Date ? 
                          (Date) data.get("updatedAt") : new Date());
        group.setActive(data.get("active") instanceof Boolean ? 
                       (Boolean) data.get("active") : true);
        
        return group;
    }
}