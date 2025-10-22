package com.example.demo.service;

import com.example.demo.dao.SceneSubmissionDao;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Video compilation implementation using GCS compose or ffmpeg fallback
 */
@Service
public class VideoCompilationServiceImpl implements VideoCompilationService {

    @Autowired(required = false)
    private FirebaseStorageService firebaseStorageService;

    @Autowired
    private Firestore db;

    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;

    @Autowired
    private com.example.demo.ai.shared.GcsFileResolver gcsFileResolver;

    @Override
    public String compileVideo(String templateId, String userId, String compiledBy) {
        try {
            String compositeVideoId = userId + "_" + templateId;
            DocumentSnapshot videoSnap = db.collection("submittedVideos").document(compositeVideoId).get().get();
            if (!videoSnap.exists()) {
                throw new NoSuchElementException("submittedVideos not found: " + compositeVideoId);
            }

            // Gather sceneIds in numeric order from submittedVideos.scenes
            Map<String, Object> scenesMap = (Map<String, Object>) videoSnap.get("scenes");
            if (scenesMap == null || scenesMap.isEmpty()) {
                throw new IllegalStateException("No scenes to compile for: " + compositeVideoId);
            }
            List<Integer> sceneNumbers = new ArrayList<>();
            for (String key : scenesMap.keySet()) {
                try { sceneNumbers.add(Integer.parseInt(key)); } catch (NumberFormatException ignore) {}
            }
            Collections.sort(sceneNumbers);

            List<String> sourceUrls = new ArrayList<>();
            for (Integer num : sceneNumbers) {
                Object val = scenesMap.get(String.valueOf(num));
                if (val instanceof Map) {
                    String sceneId = (String) ((Map<String, Object>) val).get("sceneId");
                    if (sceneId != null) {
                        var sub = sceneSubmissionDao.findById(sceneId);
                        if (sub != null && sub.getVideoUrl() != null) {
                            sourceUrls.add(sub.getVideoUrl());
                        }
                    }
                }
            }
            if (sourceUrls.isEmpty()) {
                throw new IllegalStateException("No source scene videos with URLs for: " + compositeVideoId);
            }

            String destObject = String.format("videos/%s/%s/compiled.mp4", userId, compositeVideoId);

            // Important: Do NOT use GCS compose for MP4 videos.
            // MP4 containers have a single moov/metadata atom; byte-wise composition creates an invalid file
            // that most players will only play the first segment of. Always use ffmpeg to concat properly.
            return ffmpegConcatAndUpload(sourceUrls, destObject);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile video: " + e.getMessage(), e);
        }
    }

    private String ffmpegConcatAndUpload(List<String> sourceUrls, String destObject) throws Exception {
        // Resolve each source URL via GCS client (authenticated) to avoid 403s
        List<java.io.File> tempFiles = new ArrayList<>();
        List<com.example.demo.ai.shared.GcsFileResolver.ResolvedFile> resolvedHandles = new ArrayList<>();
        java.io.File listFile = null;
        try {
            for (String url : sourceUrls) {
                // Use GcsFileResolver for gs:// or https://storage.googleapis.com URLs
                com.example.demo.ai.shared.GcsFileResolver.ResolvedFile resolved = gcsFileResolver.resolve(url);
                resolvedHandles.add(resolved);
                java.io.File f = new java.io.File(resolved.getPathAsString());
                if (!f.exists() || f.length() == 0) {
                    throw new IllegalStateException("Resolved file missing or empty for URL: " + url);
                }
                tempFiles.add(f);
            }
            // Create concat list file for ffmpeg
            listFile = java.io.File.createTempFile("concat-", ".txt");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(listFile, java.nio.charset.StandardCharsets.UTF_8)) {
                for (java.io.File f : tempFiles) {
                    pw.println("file '" + f.getAbsolutePath().replace("'", "\\'") + "'");
                }
            }
            // Run ffmpeg concat demuxer
            java.io.File outFile = java.io.File.createTempFile("compiled-", ".mp4");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listFile.getAbsolutePath(),
                    "-c", "copy", outFile.getAbsolutePath()
            );
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0) {
                // Retry with re-encode to handle mismatched codecs/parameters
                System.err.println("[Compile] ffmpeg stream-copy concat failed (code=" + code + "), retrying with re-encode...");
                outFile.delete();
                outFile = java.io.File.createTempFile("compiled-", ".mp4");
                ProcessBuilder pbReencode = new ProcessBuilder(
                        "ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listFile.getAbsolutePath(),
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                        "-c:a", "aac", "-b:a", "192k",
                        "-movflags", "+faststart",
                        outFile.getAbsolutePath()
                );
                Process p2 = pbReencode.start();
                int code2 = p2.waitFor();
                if (code2 != 0) {
                    throw new RuntimeException("ffmpeg concat (re-encode) failed with exit code " + code2);
                }
            }
            if (firebaseStorageService == null) {
                throw new IllegalStateException("FirebaseStorageService not available for upload");
            }
            String url = firebaseStorageService.uploadFile(outFile, destObject, "video/mp4");
            outFile.delete();
            return url;
        } finally {
            // Close resolved handles (will delete temp files they created)
            for (com.example.demo.ai.shared.GcsFileResolver.ResolvedFile rf : resolvedHandles) {
                try { if (rf != null) rf.close(); } catch (Exception ignored) {}
            }
            if (listFile != null) try { listFile.delete(); } catch (Exception ignored) {}
        }
    }
    
    @Override
    public String compileVideoWithBGM(String templateId, String userId, String compiledBy, List<String> bgmUrls, double bgmVolume) {
        try {
            // First compile video without BGM
            String videoUrl = compileVideo(templateId, userId, compiledBy);
            
            // If no BGM specified, return video as-is
            if (bgmUrls == null || bgmUrls.isEmpty()) {
                return videoUrl;
            }
            
            // Download compiled video
            com.example.demo.ai.shared.GcsFileResolver.ResolvedFile videoFile = gcsFileResolver.resolve(videoUrl);
            
            // Download BGM files
            List<java.io.File> bgmFiles = new ArrayList<>();
            List<com.example.demo.ai.shared.GcsFileResolver.ResolvedFile> bgmHandles = new ArrayList<>();
            
            try {
                for (String bgmUrl : bgmUrls) {
                    com.example.demo.ai.shared.GcsFileResolver.ResolvedFile bgmHandle = gcsFileResolver.resolve(bgmUrl);
                    bgmHandles.add(bgmHandle);
                    bgmFiles.add(bgmHandle.getFile());
                }
                
                // Get video duration
                double videoDuration = getVideoDuration(videoFile.getFile());
                
                // Create BGM concat file (loop if needed)
                java.io.File bgmConcatFile = createBGMConcatFile(bgmFiles, videoDuration);
                
                // Mix video with BGM
                java.io.File outputFile = java.io.File.createTempFile("compiled-bgm-", ".mp4");
                
                try {
                    mixVideoWithBGM(videoFile.getFile(), bgmConcatFile, outputFile, bgmVolume);
                    
                    // Upload final video
                    String compositeVideoId = userId + "_" + templateId;
                    String destObject = String.format("videos/%s/%s/compiled_bgm.mp4", userId, compositeVideoId);
                    
                    com.google.cloud.storage.BlobInfo blobInfo = com.google.cloud.storage.BlobInfo
                        .newBuilder(firebaseStorageService.getBucketName(), destObject)
                        .setContentType("video/mp4")
                        .build();
                    
                    firebaseStorageService.getStorage().create(blobInfo, java.nio.file.Files.readAllBytes(outputFile.toPath()));
                    
                    return String.format("https://storage.googleapis.com/%s/%s", 
                        firebaseStorageService.getBucketName(), destObject);
                        
                } finally {
                    outputFile.delete();
                    bgmConcatFile.delete();
                }
                
            } finally {
                videoFile.close();
                for (com.example.demo.ai.shared.GcsFileResolver.ResolvedFile handle : bgmHandles) {
                    try { handle.close(); } catch (Exception ignored) {}
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile video with BGM: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get video duration in seconds
     */
    private double getVideoDuration(java.io.File videoFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", videoFile.getAbsolutePath()
        );
        Process proc = pb.start();
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(proc.getInputStream())
        );
        String durationStr = reader.readLine();
        proc.waitFor();
        return Double.parseDouble(durationStr);
    }
    
    /**
     * Create concatenated BGM file that loops to match video duration
     */
    private java.io.File createBGMConcatFile(List<java.io.File> bgmFiles, double videoDuration) throws Exception {
        // Calculate total BGM duration
        double totalBGMDuration = 0;
        for (java.io.File bgmFile : bgmFiles) {
            totalBGMDuration += getVideoDuration(bgmFile);
        }
        
        // Create concat list with looping
        java.io.File concatList = java.io.File.createTempFile("bgm-concat-", ".txt");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(concatList, java.nio.charset.StandardCharsets.UTF_8)) {
            double currentDuration = 0;
            while (currentDuration < videoDuration) {
                for (java.io.File bgmFile : bgmFiles) {
                    pw.println("file '" + bgmFile.getAbsolutePath().replace("'", "'\\''") + "'");
                    currentDuration += getVideoDuration(bgmFile);
                    if (currentDuration >= videoDuration) break;
                }
            }
        }
        
        // Concatenate BGM files
        java.io.File concatenatedBGM = java.io.File.createTempFile("bgm-full-", ".mp3");
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", concatList.getAbsolutePath(),
            "-t", String.valueOf(videoDuration), // Trim to video duration
            "-c", "copy", concatenatedBGM.getAbsolutePath()
        );
        Process proc = pb.start();
        int exitCode = proc.waitFor();
        concatList.delete();
        
        if (exitCode != 0) {
            throw new RuntimeException("Failed to concatenate BGM files");
        }
        
        return concatenatedBGM;
    }
    
    /**
     * Mix video with background music
     */
    private void mixVideoWithBGM(java.io.File videoFile, java.io.File bgmFile, java.io.File outputFile, double bgmVolume) throws Exception {
        // Use FFmpeg to mix video with BGM
        // -filter_complex "[1:a]volume=<volume>[a1];[0:a][a1]amix=inputs=2:duration=shortest[aout]"
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-i", videoFile.getAbsolutePath(),
            "-i", bgmFile.getAbsolutePath(),
            "-filter_complex", String.format("[1:a]volume=%.2f[a1];[0:a][a1]amix=inputs=2:duration=shortest[aout]", bgmVolume),
            "-map", "0:v",
            "-map", "[aout]",
            "-c:v", "copy",
            "-c:a", "aac",
            "-b:a", "192k",
            outputFile.getAbsolutePath()
        );
        
        Process proc = pb.start();
        int exitCode = proc.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Failed to mix video with BGM");
        }
    }
}
