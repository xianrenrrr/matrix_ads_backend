package com.example.demo.dao;

import com.example.demo.model.TemplateFolder;
import java.util.List;

public interface TemplateFolderDao {
    String createFolder(TemplateFolder folder) throws Exception;
    TemplateFolder getFolder(String folderId) throws Exception;
    List<TemplateFolder> getFoldersByUser(String userId) throws Exception;
    List<TemplateFolder> getFoldersByParent(String parentId) throws Exception;
    void updateFolder(TemplateFolder folder) throws Exception;
    void deleteFolder(String folderId) throws Exception;
}
