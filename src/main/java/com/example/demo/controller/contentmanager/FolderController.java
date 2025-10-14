package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateFolderDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.model.TemplateFolder;
import com.example.demo.model.ManualTemplate;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/content-manager/folders")
@CrossOrigin(origins = {"http://localhost:4040", "https://matrix-ads-frontend.onrender.com"})
public class FolderController {
    
    @Autowired
    private TemplateFolderDao folderDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private I18nService i18nService;
    
    // Create folder
    @PostMapping
    public ResponseEntity<ApiResponse<TemplateFolder>> createFolder(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Accept-Language", required = false) String lang) throws Exception {
        
        String userId = (String) request.get("userId");
        String name = (String) request.get("name");
        String parentId = (String) request.get("parentId");
        
        TemplateFolder folder = new TemplateFolder();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setParentId(parentId);
        
        String folderId = folderDao.createFolder(folder);
        folder.setId(folderId);
        
        return ResponseEntity.ok(ApiResponse.ok("Folder created", folder));
    }
    
    // List user's folders
    @GetMapping
    public ResponseEntity<ApiResponse<List<TemplateFolder>>> getFolders(
            @RequestParam String userId,
            @RequestHeader(value = "Accept-Language", required = false) String lang) throws Exception {
        
        List<TemplateFolder> folders = folderDao.getFoldersByUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("Folders retrieved", folders));
    }
    
    // Rename folder or move folder to another parent
    @PutMapping("/{folderId}")
    public ResponseEntity<ApiResponse<TemplateFolder>> updateFolder(
            @PathVariable String folderId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Accept-Language", required = false) String lang) throws Exception {
        
        TemplateFolder folder = folderDao.getFolder(folderId);
        if (folder == null) {
            throw new NoSuchElementException("Folder not found");
        }
        
        String newName = (String) request.get("name");
        if (newName != null) {
            folder.setName(newName);
        }
        
        // Support moving folder to different parent
        if (request.containsKey("parentId")) {
            String newParentId = (String) request.get("parentId");
            
            // Prevent moving folder into itself or its descendants
            if (newParentId != null && newParentId.equals(folderId)) {
                throw new IllegalArgumentException("Cannot move folder into itself");
            }
            
            // Check if newParentId is a descendant of folderId
            if (newParentId != null) {
                String checkParent = newParentId;
                while (checkParent != null) {
                    if (checkParent.equals(folderId)) {
                        throw new IllegalArgumentException("Cannot move folder into its descendant");
                    }
                    TemplateFolder parentFolder = folderDao.getFolder(checkParent);
                    checkParent = parentFolder != null ? parentFolder.getParentId() : null;
                }
            }
            
            folder.setParentId(newParentId);
        }
        
        folder.setUpdatedAt(new Date());
        folderDao.updateFolder(folder);
        
        return ResponseEntity.ok(ApiResponse.ok("Folder updated", folder));
    }
    
    // Delete folder (CASCADE DELETE - deletes all contents)
    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteFolder(
            @PathVariable String folderId,
            @RequestHeader(value = "Accept-Language", required = false) String lang) throws Exception {
        
        // Recursively delete all subfolders and templates
        int deletedFolders = deleteFolderRecursive(folderId);
        int deletedTemplates = deleteTemplatesInFolder(folderId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("deletedFolders", deletedFolders);
        result.put("deletedTemplates", deletedTemplates);
        
        return ResponseEntity.ok(ApiResponse.ok("Folder and contents deleted", result));
    }
    
    // Helper: Recursively delete folder and all subfolders
    private int deleteFolderRecursive(String folderId) throws Exception {
        int count = 0;
        
        // Find all subfolders
        List<TemplateFolder> subfolders = folderDao.getFoldersByParent(folderId);
        
        // Recursively delete each subfolder
        for (TemplateFolder subfolder : subfolders) {
            count += deleteFolderRecursive(subfolder.getId());
        }
        
        // Delete this folder
        folderDao.deleteFolder(folderId);
        count++;
        
        return count;
    }
    
    // Helper: Delete all templates in folder (and subfolders)
    private int deleteTemplatesInFolder(String folderId) throws Exception {
        int count = 0;
        
        // Get all templates in this folder
        List<ManualTemplate> templates = templateDao.getTemplatesByFolder(folderId);
        
        // Delete each template
        for (ManualTemplate template : templates) {
            templateDao.deleteTemplate(template.getId());
            count++;
        }
        
        // Recursively delete templates in subfolders
        List<TemplateFolder> subfolders = folderDao.getFoldersByParent(folderId);
        for (TemplateFolder subfolder : subfolders) {
            count += deleteTemplatesInFolder(subfolder.getId());
        }
        
        return count;
    }
}
