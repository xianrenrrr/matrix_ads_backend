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
        // Download each source URL to temp file
        List<java.io.File> tempFiles = new ArrayList<>();
        java.io.File listFile = null;
        try {
            for (String url : sourceUrls) {
                java.io.File f = java.io.File.createTempFile("scene-", ".mp4");
                try (java.io.InputStream in = new java.net.URL(url).openStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
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
            for (java.io.File f : tempFiles) try { f.delete(); } catch (Exception ignored) {}
            if (listFile != null) try { listFile.delete(); } catch (Exception ignored) {}
        }
    }
}
