package com.example.demo.controller.contentmanager;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.UserDao;
import com.example.demo.model.ManualTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/content-manager/templates")
public class ContentManager {
    private final TemplateDao templateDao;
    private final UserDao userDao;

    @Autowired
    public ContentManager(TemplateDao templateDao, UserDao userDao) {
        this.templateDao = templateDao;
        this.userDao = userDao;
    }

    @PostMapping
    public ResponseEntity<ManualTemplate> createTemplate(@RequestBody com.example.demo.model.CreateTemplateRequest request) {
        String userId = request.getUserId();
        ManualTemplate manualTemplate = request.getManualTemplate();
        manualTemplate.setUserId(userId);
        try {
            String templateId = templateDao.createTemplate(manualTemplate);
            userDao.addCreatedTemplate(userId, templateId); // Add templateId to created_template field in user doc
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(manualTemplate, HttpStatus.CREATED);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TemplateSummary>> getTemplatesByUserId(@PathVariable String userId) {
        try {
            List<ManualTemplate> templates = templateDao.getTemplatesByUserId(userId);
            System.out.println("Templates: " + templates);
            List<TemplateSummary> summaries = templates.stream()
                .map(t -> new TemplateSummary(t.getId(), t.getTemplateTitle()))
                .toList();
            return ResponseEntity.ok(summaries);
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // DTO for summary
    public static class TemplateSummary {
        private String id;
        private String templateTitle;
        public TemplateSummary(String id, String templateTitle) {
            this.id = id;
            this.templateTitle = templateTitle;
        }
        public String getId() { return id; }
        public String getTemplateTitle() { return templateTitle; }
        public void setId(String id) { this.id = id; }
        public void setTemplateTitle(String templateTitle) { this.templateTitle = templateTitle; }
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ManualTemplate> getTemplateById(@PathVariable String templateId) {
        try {
            ManualTemplate template = templateDao.getTemplate(templateId);
            if (template != null) {
                return ResponseEntity.ok(template);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId, @RequestParam String userId) {
        try {
            boolean deleted = templateDao.deleteTemplate(templateId);
            if (deleted) {
                userDao.removeCreatedTemplate(userId, templateId); // Remove templateId from created_template field in user doc
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Add a template to created_Templates
    @PostMapping("/users/{userId}/created-template/{templateId}")
    public ResponseEntity<Void> addCreatedTemplate(@PathVariable String userId, @PathVariable String templateId) {
        try {
            userDao.addCreatedTemplate(userId, templateId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Remove a template from created_Templates
    @DeleteMapping("/users/{userId}/created-template/{templateId}")
    public ResponseEntity<Void> removeCreatedTemplate(@PathVariable String userId, @PathVariable String templateId) {
        try {
            userDao.removeCreatedTemplate(userId, templateId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Get all templates in created_Templates
    @GetMapping("/users/{userId}/created-template")
    public ResponseEntity<java.util.Map<String, Boolean>> getCreatedTemplates(@PathVariable String userId) {
        try {
            java.util.Map<String, Boolean> createdTemplates = userDao.getCreatedTemplates(userId);
            return ResponseEntity.ok(createdTemplates);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

