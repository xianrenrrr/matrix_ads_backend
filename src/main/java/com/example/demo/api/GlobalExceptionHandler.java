package com.example.demo.api;

import com.example.demo.service.I18nService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.access.AccessDeniedException; // Optional if security not enabled
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * Global exception handler for consistent API responses
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private I18nService i18nService;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = i18nService.getMessage("bad.request", "en");
        ApiResponse<Void> response = ApiResponse.fail(message, "Validation failed: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
        String message = i18nService.getMessage("user.not.found", "en");
        ApiResponse<Void> response = ApiResponse.fail(message, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Optional: Uncomment if Spring Security is enabled
    // @ExceptionHandler(AccessDeniedException.class)
    // public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    //     String message = i18nService.getMessage("forbidden", "en");
    //     ApiResponse<Void> response = ApiResponse.fail(message, ex.getMessage());
    //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    // }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = i18nService.getMessage("bad.request", "en");
        ApiResponse<Void> response = ApiResponse.fail(message, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        String message = i18nService.getMessage("server.error", "en");
        ApiResponse<Void> response = ApiResponse.fail(message, ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}