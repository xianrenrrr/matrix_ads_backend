package com.example.demo.controller;

import com.example.demo.dao.TemplateDao;
import com.example.demo.model.ManualTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/templates")
public class TemplateController {
    @Autowired
    private TemplateDao templateDao;

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
}
