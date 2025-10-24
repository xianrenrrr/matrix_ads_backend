package com.example.demo.controller.contentmanager;

import com.example.demo.api.ApiResponse;
import com.example.demo.dao.BackgroundMusicDao;
import com.example.demo.model.BackgroundMusic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/content-manager/bgm")
@CrossOrigin(origins = "*")
public class BackgroundMusicController {
    private static final Logger log = LoggerFactory.getLogger(BackgroundMusicController.class);
    
    @Autowired
    private BackgroundMusicDao bgmDao;
    
    /**
     * Upload background music audio file
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<BackgroundMusic>> uploadBGM(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description) {
        
        try {
            log.info("Uploading BGM for user: {}", userId);
            
            // DAO handles validation, upload, and save
            BackgroundMusic bgm = bgmDao.uploadAndSaveBackgroundMusic(file, userId, title, description);
            
            log.info("BGM uploaded successfully: {}", bgm.getId());
            return ResponseEntity.ok(ApiResponse.ok("BGM uploaded successfully", bgm));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            log.error("Error uploading BGM", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("Failed to upload BGM: " + e.getMessage()));
        }
    }
    
    /**
     * Get all BGM for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<BackgroundMusic>>> getUserBGM(@PathVariable String userId) {
        try {
            List<BackgroundMusic> bgmList = bgmDao.getBackgroundMusicByUserId(userId);
            return ResponseEntity.ok(ApiResponse.ok("BGM list retrieved", bgmList));
        } catch (Exception e) {
            log.error("Error retrieving BGM list", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("Failed to retrieve BGM list"));
        }
    }
    
    /**
     * Delete BGM
     */
    @DeleteMapping("/{bgmId}")
    public ResponseEntity<ApiResponse<Void>> deleteBGM(@PathVariable String bgmId) {
        try {
            bgmDao.deleteBackgroundMusic(bgmId);
            return ResponseEntity.ok(ApiResponse.ok("BGM deleted successfully", null));
        } catch (Exception e) {
            log.error("Error deleting BGM", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("Failed to delete BGM"));
        }
    }
    
}
