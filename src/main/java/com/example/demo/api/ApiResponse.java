package com.example.demo.api;

/**
 * Standardized API response wrapper for all controller endpoints
 */
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String error;
    
    // Private constructor to force use of static factory methods
    private ApiResponse(boolean success, String message, T data, String error) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.error = error;
    }
    
    // Static factory methods for success responses
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, null);
    }
    
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }
    
    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null, null);
    }
    
    // Static factory methods for failure responses
    public static <T> ApiResponse<T> fail(String error) {
        return new ApiResponse<>(false, null, null, error);
    }
    
    public static <T> ApiResponse<T> fail(String message, String error) {
        return new ApiResponse<>(false, message, null, error);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getError() { return error; }
    
    // Setters (for JSON serialization frameworks)
    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
    public void setData(T data) { this.data = data; }
    public void setError(String error) { this.error = error; }
}