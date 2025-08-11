package com.example.demo.controller.contentcreator;

import com.example.demo.model.ManualTemplate;

import com.example.demo.dao.UserDao;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-creator")
public class UserController {
    @Autowired
    private Firestore db;
    
    @Autowired
    private UserDao userDao;


    // Get subscribed templates (Content Creator)
    @GetMapping("/users/{userId}/subscribed-templates")
    public ResponseEntity<List<ManualTemplate>> getSubscribedTemplates(@PathVariable String userId) throws ExecutionException, InterruptedException {
        try {
            
            // Get user's subscribed templates using UserDao
            Map<String, Boolean> subscribedTemplatesMap = userDao.getSubscribedTemplates(userId);
            
            List<ManualTemplate> templates = new ArrayList<>();
            
            // Get template details for each subscribed template
            for (String templateId : subscribedTemplatesMap.keySet()) {
                if (Boolean.TRUE.equals(subscribedTemplatesMap.get(templateId))) {
                    try {
                        DocumentReference templateRef = db.collection("templates").document(templateId);
                        DocumentSnapshot templateSnap = templateRef.get().get();
                        if (templateSnap.exists()) {
                            ManualTemplate template = templateSnap.toObject(ManualTemplate.class);
                            if (template != null) {
                                template.setId(templateId); // Ensure ID is set
                                templates.add(template);
                            }
                        } else {
                            // Remove non-existent template from user's subscriptions
                            userDao.removeSubscribedTemplate(userId, templateId);
                        }
                    } catch (Exception e) {
                    }
                }
            }
            
            return ResponseEntity.ok(templates);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }


        // Inject TemplateDao for template access
        @Autowired
        private com.example.demo.dao.TemplateDao templateDao;
    
        // Get template details (Content Creator)
        @GetMapping("/templates/{templateId}")
        public ResponseEntity<ManualTemplate> getTemplateByIdForContentCreator(@PathVariable String templateId) {
            try {
                ManualTemplate template = templateDao.getTemplate(templateId);
                if (template != null) {
                    return ResponseEntity.ok(template);
                } else {
                    return ResponseEntity.notFound().build();
                }
            } catch (Exception e) {
                return ResponseEntity.internalServerError().build();
            }
        }
}
