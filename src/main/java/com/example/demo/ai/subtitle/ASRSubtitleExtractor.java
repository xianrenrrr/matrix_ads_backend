package com.example.demo.ai.subtitle;

import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionResult;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionQueryParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ASR (Automatic Speech Recognition) based subtitle extraction using Alibaba Cloud Qwen
 * 
 * API Documentation: https://help.aliyun.com/zh/model-studio/recording-file-recognition
 * 
 * Implementation:
 * 1. Extract audio from video using FFmpeg
 * 2. Upload audio to Alibaba Cloud ASR (Qwen)
 * 3. Parse ASR response to SubtitleSegment list
 * 4. Return segments with timestamps
 */
@Service
public class ASRSubtitleExtractor {
    private static final Logger log = LoggerFactory.getLogger(ASRSubtitleExtractor.class);
    
    @Value("${AI_QWEN_API_KEY:}")
    private String apiKey;
    
    private static final String MODEL = "sensevoice-v1";
    private static final int MAX_WAIT_SECONDS = 300; // 5 minutes max wait
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Extract subtitles using ASR
     * 
     * @param videoUrl GCS URL or local path of video
     * @param language Language code (zh for Chinese, en for English)
     * @return List of subtitle segments
     */
    public List<SubtitleSegment> extract(String videoUrl, String language) {
        log.info("Starting ASR subtitle extraction for video: {}, language: {}", videoUrl, language);
        
        try {
            // Step 1: Extract audio from video
            File audioFile = extractAudio(videoUrl);
            log.info("Audio extracted to: {}", audioFile.getAbsolutePath());
            
            // Step 2: Call Alibaba Cloud ASR API
            List<SubtitleSegment> segments = callQwenASR(audioFile, language);
            log.info("ASR extraction completed. Found {} segments", segments.size());
            
            // Step 3: Clean up temporary audio file
            audioFile.delete();
            
            return segments;
            
        } catch (Exception e) {
            log.error("ASR subtitle extraction failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Extract audio from video using FFmpeg
     * Converts to WAV format required by ASR API
     */
    private File extractAudio(String videoUrl) throws Exception {
        log.info("Extracting audio from video: {}", videoUrl);
        
        // Create temp file for audio
        Path tempAudio = Files.createTempFile("asr_audio_", ".wav");
        
        // Download video if it's a URL
        File videoFile;
        if (videoUrl.startsWith("http")) {
            log.info("Downloading video from URL: {}", videoUrl);
            Path tempVideo = Files.createTempFile("video_", ".mp4");
            try (InputStream in = new URL(videoUrl).openStream()) {
                Files.copy(in, tempVideo, StandardCopyOption.REPLACE_EXISTING);
            }
            videoFile = tempVideo.toFile();
            log.info("Video downloaded to: {}", tempVideo);
        } else {
            videoFile = new File(videoUrl);
        }
        
        // Extract audio using FFmpeg
        // Format: 16kHz, mono, PCM 16-bit (required by Alibaba Cloud ASR)
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-i", videoFile.getAbsolutePath(),
            "-vn",  // No video
            "-acodec", "pcm_s16le",  // PCM 16-bit
            "-ar", "16000",  // 16kHz sample rate
            "-ac", "1",  // Mono
            "-y",  // Overwrite output
            tempAudio.toString()
        );
        
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg audio extraction failed with exit code: " + exitCode);
        }
        
        log.info("Audio extraction completed: {}", tempAudio);
        return tempAudio.toFile();
    }
    
    /**
     * Call Alibaba Cloud Qwen ASR API
     * Documentation: https://help.aliyun.com/zh/model-studio/recording-file-recognition
     */
    private List<SubtitleSegment> callQwenASR(File audioFile, String language) throws Exception {
        log.info("Calling Qwen ASR API for file: {}", audioFile.getName());
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("AI_QWEN_API_KEY is not configured");
        }
        
        // Step 1: Submit transcription task
        String taskId = submitTranscriptionTask(audioFile, language);
        log.info("Transcription task submitted. Task ID: {}", taskId);
        
        // Step 2: Poll for task completion
        String transcriptionUrl = waitForTaskCompletion(taskId);
        log.info("Transcription completed. Result URL: {}", transcriptionUrl);
        
        // Step 3: Download and parse transcription result
        List<SubtitleSegment> segments = parseTranscriptionResult(transcriptionUrl);
        log.info("Parsed {} subtitle segments", segments.size());
        
        return segments;
    }
    
    /**
     * Submit transcription task to Alibaba Cloud
     */
    private String submitTranscriptionTask(File audioFile, String language) throws Exception {
        try {
            Transcription transcription = new Transcription();
            
            TranscriptionParam param = TranscriptionParam.builder()
                .model(MODEL)
                .fileUrls(List.of(audioFile.toURI().toString()))
                .apiKey(apiKey)
                .build();
            
            TranscriptionResult result = transcription.asyncCall(param);
            
            if (result.getOutput() == null || result.getOutput().getTaskId() == null) {
                throw new RuntimeException("Failed to submit transcription task: " + result);
            }
            
            return result.getOutput().getTaskId();
            
        } catch (NoApiKeyException e) {
            throw new IllegalStateException("API key is required for ASR", e);
        } catch (InputRequiredException e) {
            throw new IllegalArgumentException("Invalid input for ASR", e);
        }
    }
    
    /**
     * Wait for transcription task to complete
     * Polls the task status until it's SUCCEEDED or FAILED
     */
    private String waitForTaskCompletion(String taskId) throws Exception {
        Transcription transcription = new Transcription();
        
        long startTime = System.currentTimeMillis();
        long maxWaitMs = MAX_WAIT_SECONDS * 1000L;
        
        while (true) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > maxWaitMs) {
                throw new RuntimeException("Transcription task timed out after " + MAX_WAIT_SECONDS + " seconds");
            }
            
            // Query task status
            TranscriptionQueryParam queryParam = TranscriptionQueryParam.builder()
                .taskId(taskId)
                .apiKey(apiKey)
                .build();
            
            TranscriptionResult result = transcription.wait(queryParam);
            
            if (result.getOutput() == null) {
                throw new RuntimeException("Failed to query transcription task: " + result);
            }
            
            String status = result.getOutput().getTaskStatus();
            log.info("Task {} status: {}", taskId, status);
            
            if ("SUCCEEDED".equals(status)) {
                // Task completed successfully
                if (result.getOutput().getResults() == null || result.getOutput().getResults().isEmpty()) {
                    throw new RuntimeException("No transcription results found");
                }
                
                String transcriptionUrl = result.getOutput().getResults().get(0).getTranscriptionUrl();
                if (transcriptionUrl == null || transcriptionUrl.isEmpty()) {
                    throw new RuntimeException("Transcription URL is empty");
                }
                
                return transcriptionUrl;
                
            } else if ("FAILED".equals(status)) {
                throw new RuntimeException("Transcription task failed: " + result);
                
            } else if ("RUNNING".equals(status) || "PENDING".equals(status)) {
                // Task still in progress, wait and retry
                Thread.sleep(2000); // Wait 2 seconds before next poll
                
            } else {
                throw new RuntimeException("Unknown task status: " + status);
            }
        }
    }
    
    /**
     * Download and parse transcription result from URL
     * Converts Qwen ASR format to SubtitleSegment list
     */
    private List<SubtitleSegment> parseTranscriptionResult(String transcriptionUrl) throws Exception {
        log.info("Downloading transcription result from: {}", transcriptionUrl);
        
        // Download JSON result
        String jsonContent;
        try (InputStream in = new URL(transcriptionUrl).openStream()) {
            jsonContent = new String(in.readAllBytes(), "UTF-8");
        }
        
        log.debug("Transcription JSON: {}", jsonContent);
        
        // Parse JSON
        JsonNode root = objectMapper.readTree(jsonContent);
        List<SubtitleSegment> segments = new ArrayList<>();
        
        // Navigate to transcripts array
        JsonNode transcripts = root.path("transcripts");
        if (!transcripts.isArray() || transcripts.isEmpty()) {
            log.warn("No transcripts found in result");
            return segments;
        }
        
        // Get first transcript (channel 0)
        JsonNode transcript = transcripts.get(0);
        JsonNode sentences = transcript.path("sentences");
        
        if (!sentences.isArray()) {
            log.warn("No sentences found in transcript");
            return segments;
        }
        
        // Convert each sentence to SubtitleSegment
        for (JsonNode sentence : sentences) {
            long beginTime = sentence.path("begin_time").asLong();
            long endTime = sentence.path("end_time").asLong();
            String text = sentence.path("text").asText();
            
            if (text != null && !text.isEmpty()) {
                SubtitleSegment segment = new SubtitleSegment(beginTime, endTime, text, 1.0);
                segments.add(segment);
                log.debug("Parsed segment: {}", segment);
            }
        }
        
        log.info("Successfully parsed {} subtitle segments", segments.size());
        return segments;
    }
}
