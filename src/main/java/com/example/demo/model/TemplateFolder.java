package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateFolder {
    private String id;
    private String name;
    private String parentId;  // null = root level
    private String userId;    // owner
    private Date createdAt;
    private Date updatedAt;
    private String color;     // optional: folder color
    
    public TemplateFolder() {
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Date getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
}
