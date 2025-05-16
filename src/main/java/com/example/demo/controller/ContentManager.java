package com.example.demo.controller;

import com.example.demo.dao.TemplateDao;
import com.example.demo.model.ManualTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/templates")
public class ContentManager {
    private final TemplateDao templateDao;

    @Autowired
    public ContentManager(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }

    @PostMapping
    public ResponseEntity<ManualTemplate> createTemplate(@RequestBody com.example.demo.model.CreateTemplateRequest request) {
        String userId = request.getUserId();
        ManualTemplate manualTemplate = request.getManualTemplate();
        manualTemplate.setUserId(userId);
        try {
            templateDao.createTemplate(manualTemplate);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(manualTemplate, HttpStatus.CREATED);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TemplateSummary>> getTemplatesByUserId(@PathVariable String userId) {
        try {
            List<ManualTemplate> templates = templateDao.getTemplatesByUserId(userId);
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
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
        try {
            boolean deleted = templateDao.deleteTemplate(templateId);
            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
