package com.example.demo.ai.core;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized response wrapper for all AI model outputs
 * Provides consistent structure across different AI providers
 */
public class AIResponse<T> {
    private boolean success;
    private T data;
    private String error;
    private String modelUsed;
    private AIModelType modelType;
    private long processingTimeMs;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public AIResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public AIResponse(boolean success, T data) {
        this();
        this.success = success;
        this.data = data;
    }

    public static <T> AIResponse<T> success(T data) {
        return new AIResponse<>(true, data);
    }

    public static <T> AIResponse<T> error(String error) {
        AIResponse<T> response = new AIResponse<>(false, null);
        response.setError(error);
        return response;
    }

    public static <T> AIResponse<T> success(T data, String modelUsed, AIModelType modelType) {
        AIResponse<T> response = success(data);
        response.setModelUsed(modelUsed);
        response.setModelType(modelType);
        return response;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public AIModelType getModelType() {
        return modelType;
    }

    public void setModelType(AIModelType modelType) {
        this.modelType = modelType;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}