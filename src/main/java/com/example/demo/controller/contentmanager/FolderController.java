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
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FolderController.class);
    
    @Autowired
    private TemplateFolderDao folderDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private I18nService i18nService;
    
    @Autowired
    private com.example.demo.dao.UserDao userDao;
    
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
        
        // Determine the actual user ID to use for fetching folders
        // If user is an employee, show their manager's folders + their own folders
        String actualUserId = userId;
        com.example.demo.model.User user = userDao.findById(userId);
        
        List<TemplateFolder> folders = new ArrayList<>();
        
        if (user != null && "employee".equals(user.getRole()) && user.getCreatedBy() != null) {
            // Employee: get both manager's folders and employee's own folders
            String managerId = user.getCreatedBy();
            
            // Get manager's folders
            List<TemplateFolder> managerFolders = folderDao.getFoldersByUser(managerId);
            folders.addAll(managerFolders);
            
            // Get employee's own folders
            List<TemplateFolder> employeeFolders = folderDao.getFoldersByUser(userId);
            folders.addAll(employeeFolders);
            
            log.info("Employee {} viewing folders: {} manager folders + {} own folders", 
                userId, managerFolders.size(), employeeFolders.size());
        } else {
            // Manager or other role: get their own folders
            folders = folderDao.getFoldersByUser(userId);
            
            // Also get folders created by their employees
            if (user != null && "content-manager".equals(user.getRole())) {
                List<com.example.demo.model.User> employees = userDao.findByCreatedBy(userId);
                for (com.example.demo.model.User employee : employees) {
                    List<TemplateFolder> employeeFolders = folderDao.getFoldersByUser(employee.getId());
                    folders.addAll(employeeFolders);
                }
                log.info("Manager {} viewing folders: {} own folders + folders from {} employees", 
                    userId, folderDao.getFoldersByUser(userId).size(), employees.size());
            }
        }
        
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
