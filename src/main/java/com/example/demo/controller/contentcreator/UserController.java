package com.example.demo.controller.contentcreator;

import com.example.demo.model.ManualTemplate;
import com.example.demo.dao.GroupDao;
import com.example.demo.api.ApiResponse;
import com.example.demo.service.I18nService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/content-creator")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    
    @Autowired
    private GroupDao groupDao;
    
    @Autowired
    private I18nService i18nService;
    
    @Autowired
    private com.google.cloud.firestore.Firestore db;


    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    // Removed getAssignedTemplates - now using GroupController.getGroupTemplates instead
    
    // Removed getPublishStatus - no longer needed

    // Removed getTemplateByIdForContentCreator - unused endpoint (0 references)
    
}
