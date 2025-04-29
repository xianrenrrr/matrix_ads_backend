package com.example.demo;

import com.example.demo.model.ManualTemplate;
import com.example.demo.dao.TemplateDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateDao templateDao;

    @Autowired
    public TemplateController(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }

    @PostMapping
    public ResponseEntity<ManualTemplate> createTemplate(@RequestBody com.example.demo.model.CreateTemplateRequest request) {
        // Extract userId and manualTemplate from the request
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
}
