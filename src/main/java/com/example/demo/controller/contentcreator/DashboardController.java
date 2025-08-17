package com.example.demo.controller.contentcreator;

import com.example.demo.model.ManualTemplate;
import com.example.demo.api.ApiResponse;
import com.example.demo.dao.GroupDao;
import com.example.demo.dao.SubmittedVideoDao;
import com.example.demo.dao.TemplateDao;
import com.example.demo.service.I18nService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/content-creator")
public class DashboardController {
    
    @Autowired
    private GroupDao groupDao;
    
    @Autowired
    private SubmittedVideoDao submittedVideoDao;
    
    @Autowired
    private TemplateDao templateDao;
    
    @Autowired
    private I18nService i18nService;
    
    // Get dashboard statistics for content creator
    @GetMapping("/users/{userId}/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(@PathVariable String userId,
                                                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws Exception {
        String language = i18nService.detectLanguageFromHeader(acceptLanguage);
        Map<String, Object> stats = new HashMap<>();
        
        // Get assigned templates count through group using TemplateDao
        int assignedTemplatesCount = 0;
        String userGroupId = groupDao.getUserGroupId(userId);
        if (userGroupId != null) {
            List<ManualTemplate> assignedTemplates = templateDao.getTemplatesAssignedToGroup(userGroupId);
            assignedTemplatesCount = assignedTemplates.size();
        }
        
        
        // Get video counts using SubmittedVideoDao
        int submittedVideosCount = submittedVideoDao.getVideoCountByUser(userId);
        int publishedVideosCount = submittedVideoDao.getPublishedVideoCountByUser(userId);
        
        // Build response
        stats.put("assignedTemplates", assignedTemplatesCount);
        stats.put("recordedVideos", submittedVideosCount);
        stats.put("publishedVideos", publishedVideosCount);
        stats.put("userGroupId", userGroupId);
        
        String message = i18nService.getMessage("operation.success", language);
        return ResponseEntity.ok(ApiResponse.ok(message, stats));
    }
    
}