package com.example.demo.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class I18nService {
    
    private final Map<String, Map<String, String>> messages = new HashMap<>();
    
    public I18nService() {
        initializeMessages();
    }
    
    private void initializeMessages() {
        // Chinese (Simplified) Messages
        Map<String, String> zhMessages = new HashMap<>();
        zhMessages.put("login.success", "登录成功");
        zhMessages.put("login.failed", "登录失败");
        zhMessages.put("login.invalid.credentials", "用户名或密码错误");
        zhMessages.put("registration.success", "注册成功");
        zhMessages.put("registration.failed", "注册失败");
        zhMessages.put("user.not.found", "用户不存在");
        zhMessages.put("template.created", "模板创建成功");
        zhMessages.put("template.updated", "模板更新成功");
        zhMessages.put("template.deleted", "模板删除成功");
        zhMessages.put("template.groups.updated", "模板分组已更新");
        zhMessages.put("template.not.found", "模板不存在");
        zhMessages.put("video.uploaded", "视频上传成功");
        zhMessages.put("video.upload.failed", "视频上传失败");
        zhMessages.put("video.not.found", "视频不存在");
        zhMessages.put("unauthorized", "未授权访问");
        zhMessages.put("forbidden", "禁止访问");
        zhMessages.put("server.error", "服务器内部错误");
        zhMessages.put("bad.request", "请求参数错误");
        zhMessages.put("qr.generated", "二维码生成成功");
        zhMessages.put("qr.expired", "二维码已过期");
        zhMessages.put("qr.invalid", "无效的二维码");
        zhMessages.put("notification.sent", "通知发送成功");
        zhMessages.put("notification.failed", "通知发送失败");
        zhMessages.put("template.subscribed", "模板订阅成功");
        zhMessages.put("template.unsubscribed", "模板取消订阅成功");
        zhMessages.put("video.approved", "视频审核通过");
        zhMessages.put("video.rejected", "视频审核拒绝");
        zhMessages.put("operation.success", "操作成功");
        zhMessages.put("operation.failed", "操作失败");
        
        // Auth and Invite messages
        zhMessages.put("token.required", "令牌是必需的");
        zhMessages.put("token.valid", "令牌有效");
        zhMessages.put("token.validation.failed", "令牌验证失败");
        zhMessages.put("username.exists", "用户名已存在");
        zhMessages.put("phone.exists", "手机号已存在");
        zhMessages.put("email.exists", "该角色的电子邮件已存在");
        zhMessages.put("invite.not_found", "邀请未找到");
        zhMessages.put("group.inactive", "群组未激活");
        zhMessages.put("group.not_found", "群组不存在");
        zhMessages.put("user.creation.failed", "用户创建失败");
        zhMessages.put("user.joined.group", "用户已加入群组");
        zhMessages.put("registration.error", "注册过程中出现错误");
        
        // English Messages
        Map<String, String> enMessages = new HashMap<>();
        enMessages.put("login.success", "Login successful");
        enMessages.put("login.failed", "Login failed");
        enMessages.put("login.invalid.credentials", "Invalid username or password");
        enMessages.put("registration.success", "Registration successful");
        enMessages.put("registration.failed", "Registration failed");
        enMessages.put("user.not.found", "User not found");
        enMessages.put("template.created", "Template created successfully");
        enMessages.put("template.updated", "Template updated successfully");
        enMessages.put("template.deleted", "Template deleted successfully");
        enMessages.put("template.groups.updated", "Template groups updated successfully");
        enMessages.put("template.not.found", "Template not found");
        enMessages.put("video.uploaded", "Video uploaded successfully");
        enMessages.put("video.upload.failed", "Video upload failed");
        enMessages.put("video.not.found", "Video not found");
        enMessages.put("unauthorized", "Unauthorized access");
        enMessages.put("forbidden", "Access forbidden");
        enMessages.put("server.error", "Internal server error");
        enMessages.put("bad.request", "Bad request parameters");
        enMessages.put("qr.generated", "QR code generated successfully");
        enMessages.put("qr.expired", "QR code expired");
        enMessages.put("qr.invalid", "Invalid QR code");
        enMessages.put("notification.sent", "Notification sent successfully");
        enMessages.put("notification.failed", "Failed to send notification");
        enMessages.put("template.subscribed", "Template subscribed successfully");
        enMessages.put("template.unsubscribed", "Template unsubscribed successfully");
        enMessages.put("video.approved", "Video approved");
        enMessages.put("video.rejected", "Video rejected");
        enMessages.put("operation.success", "Operation successful");
        enMessages.put("operation.failed", "Operation failed");
        
        // Auth and Invite messages  
        enMessages.put("token.required", "Token is required");
        enMessages.put("token.valid", "Token is valid");
        enMessages.put("token.validation.failed", "Token validation failed");
        enMessages.put("username.exists", "Username already exists");
        enMessages.put("phone.exists", "Phone number already exists");
        enMessages.put("email.exists", "Email already exists for this role");
        enMessages.put("invite.not_found", "Invite not found");
        enMessages.put("group.inactive", "Group is not active");
        enMessages.put("group.not_found", "Group not found");
        enMessages.put("user.creation.failed", "User creation failed");
        enMessages.put("user.joined.group", "User joined group successfully");
        enMessages.put("registration.error", "Error occurred during registration");
        
        messages.put("zh", zhMessages);
        messages.put("en", enMessages);
    }
    
    public String getMessage(String key, String language) {
        // Default to Chinese if language not specified
        if (language == null || language.isEmpty()) {
            language = "zh";
        }
        
        // Normalize language code
        if (language.startsWith("zh")) {
            language = "zh";
        } else if (language.startsWith("en")) {
            language = "en";
        } else {
            language = "zh"; // Default fallback
        }
        
        Map<String, String> languageMessages = messages.get(language);
        if (languageMessages != null && languageMessages.containsKey(key)) {
            return languageMessages.get(key);
        }
        
        // Fallback to Chinese if message not found in requested language
        Map<String, String> fallbackMessages = messages.get("zh");
        if (fallbackMessages != null && fallbackMessages.containsKey(key)) {
            return fallbackMessages.get(key);
        }
        
        // Return key if no translation found
        return key;
    }
    
    public String getMessage(String key) {
        return getMessage(key, "zh");
    }
    
    // Helper method to detect language from Accept-Language header
    public String detectLanguageFromHeader(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isEmpty()) {
            return "zh";
        }
        
        // Simple language detection from Accept-Language header
        String[] languages = acceptLanguageHeader.split(",");
        for (String lang : languages) {
            String cleanLang = lang.trim().split(";")[0].toLowerCase();
            if (cleanLang.startsWith("zh")) {
                return "zh";
            } else if (cleanLang.startsWith("en")) {
                return "en";
            }
        }
        
        return "zh"; // Default to Chinese
    }
    
    // Helper method to detect language with mini program override
    public String detectLanguageFromRequest(String acceptLanguageHeader, String userAgent) {
        // If WeChat mini program (MicroMessenger), always return Chinese
        if (userAgent != null && userAgent.toLowerCase().contains("micromessenger")) {
            return "zh";
        }
        
        // Otherwise use normal detection
        return detectLanguageFromHeader(acceptLanguageHeader);
    }
}
