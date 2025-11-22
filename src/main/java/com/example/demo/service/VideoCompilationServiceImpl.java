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
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;

    @Autowired
    private Firestore db;

    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;

    // GcsFileResolver removed - now using OSS signed URLs directly

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

    // TODO: Add subtitle burning to compiled video
    // 
    // Steps to implement:
    // 1. Fetch template to get all scenes with subtitleSegments
    // 2. Generate SRT file from subtitleSegments (use SubtitleSegment.toSrt())
    // 3. Add FFmpeg subtitle filter: -vf "subtitles=subtitles.srt:force_style='FontSize=24,PrimaryColour=&HFFFFFF&'"
    // 4. Consider: Make subtitle burning optional (manager toggle in UI?)
    //
    // Example FFmpeg command with subtitles:
    // ffmpeg -f concat -i list.txt -vf "subtitles=subtitles.srt" -c:a copy output.mp4
    //
    // Benefits:
    // - Final video has burned-in subtitles for social media
    // - Viewers can read along with the video
    // - Better accessibility
    private String ffmpegConcatAndUpload(List<String> sourceUrls, String destObject) throws Exception {
        // Generate signed URLs for OSS videos to avoid 403s
        List<String> signedUrls = new ArrayList<>();
        java.io.File listFile = null;
        try {
            for (String url : sourceUrls) {
                // Generate signed URL for OSS video (2 hours expiration)
                String signedUrl = ossStorageService.generateSignedUrl(url, 2, java.util.concurrent.TimeUnit.HOURS);
                signedUrls.add(signedUrl);
            }
            // Create concat list file for ffmpeg with signed URLs
            listFile = java.io.File.createTempFile("concat-", ".txt");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(listFile, java.nio.charset.StandardCharsets.UTF_8)) {
                for (String signedUrl : signedUrls) {
                    pw.println("file '" + signedUrl + "'");
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
            if (ossStorageService == null) {
                throw new IllegalStateException("AlibabaOssStorageService not available for upload");
            }
            String url = ossStorageService.uploadFile(outFile, destObject, "video/mp4");
            outFile.delete();
            return url;
        } finally {
            // Clean up temp files
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
            
            // Generate signed URLs for video and BGM files
            String videoSignedUrl = ossStorageService.generateSignedUrl(videoUrl, 2, java.util.concurrent.TimeUnit.HOURS);
            
            List<String> bgmSignedUrls = new ArrayList<>();
            for (String bgmUrl : bgmUrls) {
                String bgmSignedUrl = ossStorageService.generateSignedUrl(bgmUrl, 2, java.util.concurrent.TimeUnit.HOURS);
                bgmSignedUrls.add(bgmSignedUrl);
            }
            
            try {
                
                // Get video duration using signed URL
                double videoDuration = getVideoDurationFromUrl(videoSignedUrl);
                
                // Create BGM concat file (loop if needed) using signed URLs
                java.io.File bgmConcatFile = createBGMConcatFileFromUrls(bgmSignedUrls, videoDuration);
                
                // Mix video with BGM
                java.io.File outputFile = java.io.File.createTempFile("compiled-bgm-", ".mp4");
                
                try {
                    mixVideoWithBGMFromUrl(videoSignedUrl, bgmConcatFile, outputFile, bgmVolume);
                    
                    // Upload final video
                    String compositeVideoId = userId + "_" + templateId;
                    String destObject = String.format("videos/%s/%s/compiled_bgm.mp4", userId, compositeVideoId);
                    
                    return ossStorageService.uploadFile(outputFile, destObject, "video/mp4");
                        
                } finally {
                    outputFile.delete();
                    bgmConcatFile.delete();
                }
                
            } finally {
                // No cleanup needed for signed URLs
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile video with BGM: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get video duration in seconds from URL
     */
    private double getVideoDurationFromUrl(String videoUrl) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", videoUrl
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
     * Get video duration in seconds from local file
     */
    private double getVideoDurationFromFile(java.io.File videoFile) throws Exception {
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
     * Create concatenated BGM file that loops to match video duration using signed URLs
     */
    private java.io.File createBGMConcatFileFromUrls(List<String> bgmUrls, double videoDuration) throws Exception {
        // Download BGM files locally first
        List<java.io.File> localBgmFiles = ossStorageService.downloadMultipleToTempFiles(bgmUrls, "bgm-", ".mp3");
        
        // Calculate total BGM duration
        double totalBGMDuration = 0;
        for (java.io.File bgmFile : localBgmFiles) {
            totalBGMDuration += getVideoDurationFromFile(bgmFile);
        }
        
        // Create concat list with looping using local file paths
        java.io.File concatList = java.io.File.createTempFile("bgm-concat-", ".txt");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(concatList, java.nio.charset.StandardCharsets.UTF_8)) {
            double currentDuration = 0;
            while (currentDuration < videoDuration) {
                for (java.io.File bgmFile : localBgmFiles) {
                    // Use absolute path and escape single quotes
                    String path = bgmFile.getAbsolutePath().replace("'", "'\\''");
                    pw.println("file '" + path + "'");
                    currentDuration += getVideoDurationFromFile(bgmFile);
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
        
        // Clean up
        concatList.delete();
        for (java.io.File bgmFile : localBgmFiles) {
            bgmFile.delete();
        }
        
        if (exitCode != 0) {
            throw new RuntimeException("Failed to concatenate BGM files");
        }
        
        return concatenatedBGM;
    }
    
    /**
     * Mix video with background music using signed URL
     */
    private void mixVideoWithBGMFromUrl(String videoUrl, java.io.File bgmFile, java.io.File outputFile, double bgmVolume) throws Exception {
        // Use FFmpeg to mix video with BGM
        // -filter_complex "[1:a]volume=<volume>[a1];[0:a][a1]amix=inputs=2:duration=shortest[aout]"
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-i", videoUrl,
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
    
    @Autowired
    private SubtitleBurningService subtitleBurningService;
    
    @Autowired
    private com.example.demo.dao.TemplateDao templateDao;
    
    @Autowired
    private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    
    @Override
    public String compileVideoWithSubtitles(String templateId, String userId, String compiledBy, SubtitleBurningService.SubtitleOptions subtitleOptions) {
        return compileVideoWithBGMAndSubtitles(templateId, userId, compiledBy, null, 0.0, subtitleOptions);
    }
    
    @Override
    public String compileVideoWithBGMAndSubtitles(String templateId, String userId, String compiledBy, List<String> bgmUrls, double bgmVolume, SubtitleBurningService.SubtitleOptions subtitleOptions) {
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

            // Get template from assignment snapshot (templateId is actually assignmentId)
            com.example.demo.model.TemplateAssignment assignment = templateAssignmentDao.getAssignment(templateId);
            if (assignment == null) {
                throw new NoSuchElementException("Assignment not found: " + templateId);
            }
            com.example.demo.model.ManualTemplate template = assignment.getTemplateSnapshot();
            if (template == null) {
                throw new NoSuchElementException("Template snapshot not found in assignment: " + templateId);
            }
            
            String destObject = String.format("videos/%s/%s/compiled_subtitled.mp4", userId, compositeVideoId);
            
            // Compile with subtitles and optional BGM
            return ffmpegConcatWithSubtitlesAndBGM(sourceUrls, template.getScenes(), bgmUrls, bgmVolume, subtitleOptions, destObject);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile video with subtitles: " + e.getMessage(), e);
        }
    }
    
    /**
     * Concatenate videos with subtitles and optional BGM
     */
    private String ffmpegConcatWithSubtitlesAndBGM(
        List<String> sourceUrls, 
        List<com.example.demo.model.Scene> scenes,
        List<String> bgmUrls,
        double bgmVolume,
        SubtitleBurningService.SubtitleOptions subtitleOptions,
        String destObject
    ) throws Exception {
        
        // Download videos locally first (more reliable than using signed URLs in concat)
        // Using centralized OSS service for all downloads
        List<java.io.File> localVideoFiles = null;
        
        java.io.File listFile = null;
        java.io.File srtFile = null;
        java.io.File bgmFile = null;
        java.io.File outFile = null;
        
        try {
            // Download all scene videos using centralized OSS service
            localVideoFiles = ossStorageService.downloadMultipleToTempFiles(sourceUrls, "scene-", ".mp4");
            
            // Create concat list file with local paths
            listFile = java.io.File.createTempFile("concat-", ".txt");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(listFile, java.nio.charset.StandardCharsets.UTF_8)) {
                for (java.io.File videoFile : localVideoFiles) {
                    // Use absolute path and escape single quotes
                    String path = videoFile.getAbsolutePath().replace("'", "'\\''");
                    pw.println("file '" + path + "'");
                }
            }
            
            // Generate SRT file from subtitleSegments
            String srtPath = null;
            if (subtitleOptions != null && scenes != null && !scenes.isEmpty()) {
                System.out.println("[Compile] 📋 Generating SRT from " + scenes.size() + " scenes");
                for (int i = 0; i < scenes.size(); i++) {
                    com.example.demo.model.Scene scene = scenes.get(i);
                    int segmentCount = (scene.getSubtitleSegments() != null) ? scene.getSubtitleSegments().size() : 0;
                    System.out.println("[Compile]   Scene " + (i+1) + ": " + segmentCount + " subtitle segments");
                }
                srtPath = subtitleBurningService.generateSrtFile(scenes);
                srtFile = new java.io.File(srtPath);
                System.out.println("[Compile] ✅ Generated SRT file: " + srtPath);
            } else {
                System.out.println("[Compile] ⚠️ No SRT file generated:");
                System.out.println("[Compile]   - subtitleOptions: " + (subtitleOptions != null ? "present" : "null"));
                System.out.println("[Compile]   - scenes: " + (scenes != null ? scenes.size() + " scenes" : "null"));
            }
            
            // Build FFmpeg command
            List<String> ffmpegCmd = new ArrayList<>();
            ffmpegCmd.add("ffmpeg");
            ffmpegCmd.add("-y");
            ffmpegCmd.add("-f");
            ffmpegCmd.add("concat");
            ffmpegCmd.add("-safe");
            ffmpegCmd.add("0");
            ffmpegCmd.add("-i");
            ffmpegCmd.add(listFile.getAbsolutePath());
            
            // Add BGM input if specified
            if (bgmUrls != null && !bgmUrls.isEmpty()) {
                // Get video duration from local files
                double videoDuration = 0;
                for (java.io.File videoFile : localVideoFiles) {
                    videoDuration += getVideoDurationFromFile(videoFile);
                }
                
                // Generate signed URLs for BGM
                List<String> bgmSignedUrls = new ArrayList<>();
                for (String bgmUrl : bgmUrls) {
                    bgmSignedUrls.add(ossStorageService.generateSignedUrl(bgmUrl, 2, java.util.concurrent.TimeUnit.HOURS));
                }
                
                // Create BGM concat file
                bgmFile = createBGMConcatFileFromUrls(bgmSignedUrls, videoDuration);
                ffmpegCmd.add("-i");
                ffmpegCmd.add(bgmFile.getAbsolutePath());
            }
            
            // Build filter complex
            StringBuilder filterComplex = new StringBuilder();
            boolean hasFilters = false;
            
            // Add subtitle filter if SRT exists
            if (srtPath != null) {
                String subtitleFilter = subtitleBurningService.buildSubtitleFilter(srtPath, subtitleOptions);
                System.out.println("[Compile] 📝 Subtitle filter: " + subtitleFilter);
                filterComplex.append("[0:v]").append(subtitleFilter).append("[v]");
                hasFilters = true;
            }
            
            // Add audio mixing if BGM exists
            if (bgmFile != null) {
                if (hasFilters) filterComplex.append(";");
                filterComplex.append(String.format("[1:a]volume=%.2f[a1];[0:a][a1]amix=inputs=2:duration=shortest[a]", bgmVolume));
                hasFilters = true;
            }
            
            // Add filter complex if we have any filters
            if (hasFilters) {
                ffmpegCmd.add("-filter_complex");
                ffmpegCmd.add(filterComplex.toString());
                
                // Map outputs
                if (srtPath != null) {
                    ffmpegCmd.add("-map");
                    ffmpegCmd.add("[v]");
                } else {
                    ffmpegCmd.add("-map");
                    ffmpegCmd.add("0:v");
                }
                
                if (bgmFile != null) {
                    ffmpegCmd.add("-map");
                    ffmpegCmd.add("[a]");
                } else {
                    ffmpegCmd.add("-map");
                    ffmpegCmd.add("0:a");
                }
                
                // Encoding settings (optimized for speed on limited resources)
                ffmpegCmd.add("-c:v");
                ffmpegCmd.add("libx264");
                ffmpegCmd.add("-preset");
                ffmpegCmd.add("ultrafast");  // Changed from veryfast to ultrafast
                ffmpegCmd.add("-crf");
                ffmpegCmd.add("28");  // Changed from 23 to 28 (lower quality but faster)
                ffmpegCmd.add("-c:a");
                ffmpegCmd.add("aac");
                ffmpegCmd.add("-b:a");
                ffmpegCmd.add("192k");
            } else {
                // No filters, just copy
                ffmpegCmd.add("-c");
                ffmpegCmd.add("copy");
            }
            
            // Output file
            outFile = java.io.File.createTempFile("compiled-", ".mp4");
            ffmpegCmd.add("-movflags");
            ffmpegCmd.add("+faststart");
            ffmpegCmd.add(outFile.getAbsolutePath());
            
            // Execute FFmpeg
            System.out.println("[Compile] FFmpeg command: " + String.join(" ", ffmpegCmd));
            ProcessBuilder pb = new ProcessBuilder(ffmpegCmd);
            pb.redirectErrorStream(true); // Merge stderr into stdout
            Process p = pb.start();
            
            // Capture output for debugging
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int code = p.waitFor();
            
            if (code != 0) {
                System.err.println("[Compile] FFmpeg failed with exit code " + code);
                System.err.println("[Compile] FFmpeg output:\n" + output.toString());
                throw new RuntimeException("ffmpeg compilation failed with exit code " + code + ". Output: " + output.toString());
            }
            
            System.out.println("[Compile] ✅ FFmpeg completed successfully");
            
            // Upload to OSS
            if (ossStorageService == null) {
                throw new IllegalStateException("AlibabaOssStorageService not available for upload");
            }
            String url = ossStorageService.uploadFile(outFile, destObject, "video/mp4");
            System.out.println("[Compile] ✅ Compiled video with subtitles uploaded: " + url);
            
            return url;
            
        } finally {
            // Clean up temp files
            if (listFile != null) try { listFile.delete(); } catch (Exception ignored) {}
            if (srtFile != null) try { srtFile.delete(); } catch (Exception ignored) {}
            if (bgmFile != null) try { bgmFile.delete(); } catch (Exception ignored) {}
            if (outFile != null) try { outFile.delete(); } catch (Exception ignored) {}
            // Clean up downloaded video files
            for (java.io.File videoFile : localVideoFiles) {
                try { videoFile.delete(); } catch (Exception ignored) {}
            }
        }
    }
}
