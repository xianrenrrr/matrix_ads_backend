package com.example.demo.api;

import com.example.demo.service.I18nService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.access.AccessDeniedException; // Optional if security not enabled
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;

/**
 * Global exception handler for consistent API responses
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private I18nService i18nService;

    /**
     * Extract language from Accept-Language header, default to Chinese ("zh")
     */
    private String getLanguageFromRequest(HttpServletRequest request) {
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && acceptLanguage.startsWith("en")) {
            return "en";
        }
        return "zh"; // Default to Chinese for MVP
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String language = getLanguageFromRequest(request);
        String message = i18nService.getMessage("bad.request", language);
        ApiResponse<Void> response = ApiResponse.fail(message, "Validation failed: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        String language = getLanguageFromRequest(request);
        String raw = ex.getMessage() != null ? ex.getMessage() : "";
        String key = raw.startsWith("Group not found") ? "group.not_found" : "user.not.found";
        String message = i18nService.getMessage(key, language);
        ApiResponse<Void> response = ApiResponse.fail(message, raw);
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
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String language = getLanguageFromRequest(request);
        String raw = ex.getMessage() != null ? ex.getMessage() : "";
        String key = switch (raw) {
            case "Username already exists" -> "username.exists";
            case "Email already exists for this role" -> "email.exists";
            case "Phone number already exists" -> "phone.exists";
            case "Group is inactive" -> "group.inactive";
            case "Group not found" -> "group.not_found";
            default -> "bad.request";
        };
        String message = i18nService.getMessage(key, language);
        ApiResponse<Void> response = ApiResponse.fail(message, raw);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, HttpServletRequest request) {
        String language = getLanguageFromRequest(request);
        String message = i18nService.getMessage("server.error", language);
        ApiResponse<Void> response = ApiResponse.fail(message, ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
