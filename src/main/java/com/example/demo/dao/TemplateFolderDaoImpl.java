package com.example.demo.dao;

import com.example.demo.model.TemplateFolder;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TemplateFolderDaoImpl implements TemplateFolderDao {
    
    @Autowired
    private Firestore db;
    
    private static final String COLLECTION = "templateFolders";
    
    @Override
    public String createFolder(TemplateFolder folder) throws Exception {
        DocumentReference docRef = db.collection(COLLECTION).document();
        folder.setId(docRef.getId());
        folder.setCreatedAt(new Date());
        folder.setUpdatedAt(new Date());
        
        Map<String, Object> data = folderToMap(folder);
        docRef.set(data).get();
        return folder.getId();
    }
    
    @Override
    public TemplateFolder getFolder(String folderId) throws Exception {
        DocumentSnapshot doc = db.collection(COLLECTION).document(folderId).get().get();
        if (!doc.exists()) {
            return null;
        }
        return mapToFolder(doc);
    }
    
    @Override
    public List<TemplateFolder> getFoldersByUser(String userId) throws Exception {
        QuerySnapshot snapshot = db.collection(COLLECTION)
            .whereEqualTo("userId", userId)
            .get()
            .get();
        
        List<TemplateFolder> folders = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            folders.add(mapToFolder(doc));
        }
        return folders;
    }
    
    @Override
    public List<TemplateFolder> getFoldersByParent(String parentId) throws Exception {
        QuerySnapshot snapshot = db.collection(COLLECTION)
            .whereEqualTo("parentId", parentId)
            .get()
            .get();
        
        List<TemplateFolder> folders = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            folders.add(mapToFolder(doc));
        }
        return folders;
    }
    
    @Override
    public void updateFolder(TemplateFolder folder) throws Exception {
        folder.setUpdatedAt(new Date());
        Map<String, Object> data = folderToMap(folder);
        db.collection(COLLECTION).document(folder.getId()).set(data).get();
    }
    
    @Override
    public void deleteFolder(String folderId) throws Exception {
        db.collection(COLLECTION).document(folderId).delete().get();
    }
    
    // Helper methods
    private Map<String, Object> folderToMap(TemplateFolder folder) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", folder.getId());
        data.put("name", folder.getName());
        data.put("parentId", folder.getParentId());
        data.put("userId", folder.getUserId());
        data.put("createdAt", folder.getCreatedAt());
        data.put("updatedAt", folder.getUpdatedAt());
        if (folder.getColor() != null) {
            data.put("color", folder.getColor());
        }
        return data;
    }
    
    private TemplateFolder mapToFolder(DocumentSnapshot doc) {
        TemplateFolder folder = new TemplateFolder();
        folder.setId(doc.getId());
        folder.setName(doc.getString("name"));
        folder.setParentId(doc.getString("parentId"));
        folder.setUserId(doc.getString("userId"));
        folder.setCreatedAt(doc.getDate("createdAt"));
        folder.setUpdatedAt(doc.getDate("updatedAt"));
        folder.setColor(doc.getString("color"));
        return folder;
    }
}
