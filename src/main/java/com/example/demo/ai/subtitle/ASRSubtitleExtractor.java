package com.example.demo.ai.subtitle;

import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionResult;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionQueryParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionTaskResult;
import com.alibaba.dashscope.common.TaskStatus;
import com.example.demo.service.AlibabaOssStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

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
    
    @Autowired
    private AlibabaOssStorageService ossStorageService;
    
    private static final String MODEL = "sensevoice-v1";
    private static final int MAX_WAIT_SECONDS = 600; // 10 minutes max wait
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
        
        File audioFile = null;
        String audioOssUrl = null;
        
        try {
            // Step 1: Extract audio from video
            audioFile = extractAudio(videoUrl);
            log.info("Audio extracted to: {}", audioFile.getAbsolutePath());
            
            // Step 2: Upload audio to OSS (required by ASR API)
            String objectKey = "asr-audio/" + System.currentTimeMillis() + "_" + audioFile.getName();
            String ossUrl = ossStorageService.uploadFile(audioFile, objectKey, "audio/wav");
            log.info("Audio uploaded to OSS: {}", ossUrl);
            
            // Step 3: Prepare URL for Alibaba Cloud access (generates signed URL)
            audioOssUrl = ossStorageService.prepareUrlForAlibabaCloud(ossUrl, 2, java.util.concurrent.TimeUnit.HOURS);
            log.info("Prepared URL for ASR (expires in 2 hours)");
            
            // Step 4: Call Alibaba Cloud ASR API with signed URL
            List<SubtitleSegment> segments = callQwenASR(audioOssUrl, language);
            log.info("ASR extraction completed. Found {} segments", segments.size());
            
            return segments;
            
        } catch (Exception e) {
            log.error("ASR subtitle extraction failed", e);
            return new ArrayList<>();
        } finally {
            // Clean up temporary audio file
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
            }
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
            
            // Use URLConnection with proper timeout settings for large files
            java.net.URLConnection connection = new URL(videoUrl).openConnection();
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(300000);   // 5 minutes for large video files
            
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempVideo, StandardCopyOption.REPLACE_EXISTING);
            }
            videoFile = tempVideo.toFile();
            log.info("Video downloaded to: {} (size: {} bytes)", tempVideo, videoFile.length());
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
     * 
     * @param audioOssUrl Public OSS URL of the audio file
     * @param language Language code (zh for Chinese, en for English)
     */
    private List<SubtitleSegment> callQwenASR(String audioOssUrl, String language) throws Exception {
        log.info("Calling Qwen ASR API for audio URL: {}", audioOssUrl);
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("AI_QWEN_API_KEY is not configured");
        }
        
        // Step 1: Submit transcription task with audio URL
        TranscriptionResult result = submitTranscriptionTask(audioOssUrl, language);
        log.info("Transcription task submitted. Task ID: {}", result.getTaskId());
        
        // Step 2: Wait for task completion (blocking call)
        TranscriptionQueryParam queryParam = TranscriptionQueryParam.FromTranscriptionParam(
            TranscriptionParam.builder()
                .model(MODEL)
                .fileUrls(List.of(audioOssUrl))
                .apiKey(apiKey)
                .build(),
            result.getTaskId()
        );
        
        TranscriptionResult finalResult = new Transcription().wait(queryParam);
        log.info("Transcription completed. Status: {}", finalResult.getTaskStatus());
        
        // Step 3: Parse transcription result
        if (finalResult.getTaskStatus() != TaskStatus.SUCCEEDED) {
            // Log detailed error information
            String errorDetails = "Status: " + finalResult.getTaskStatus();
            if (finalResult.getResults() != null && !finalResult.getResults().isEmpty()) {
                TranscriptionTaskResult taskResult = finalResult.getResults().get(0);
                errorDetails += ", SubTask Status: " + taskResult.getSubTaskStatus();
                errorDetails += ", Message: " + taskResult.getMessage();
            }
            log.error("Transcription task failed. Details: {}", errorDetails);
            log.error("Full result output: {}", finalResult.getOutput());
            throw new RuntimeException("Transcription task failed: " + errorDetails);
        }
        
        List<SubtitleSegment> segments = parseTranscriptionResult(finalResult);
        log.info("Parsed {} subtitle segments", segments.size());
        
        return segments;
    }
    
    /**
     * Submit transcription task to Alibaba Cloud
     * 
     * @param audioOssUrl Public OSS URL of the audio file (HTTP/HTTPS)
     * @param language Language code (zh for Chinese, en for English)
     */
    private TranscriptionResult submitTranscriptionTask(String audioOssUrl, String language) throws Exception {
        Transcription transcription = new Transcription();
        
        // Build transcription parameters
        // Note: fileUrls must be publicly accessible HTTP/HTTPS URLs
        TranscriptionParam param = TranscriptionParam.builder()
            .model(MODEL)
            .fileUrls(List.of(audioOssUrl))
            .apiKey(apiKey)
            .build();
        
        // Submit async task
        TranscriptionResult result = transcription.asyncCall(param);
        
        if (result.getTaskId() == null || result.getTaskId().isEmpty()) {
            throw new RuntimeException("Failed to submit transcription task: no task ID returned");
        }
        
        return result;
    }
    
    /**
     * Parse transcription result from TranscriptionResult
     * Converts Qwen ASR format to SubtitleSegment list
     */
    private List<SubtitleSegment> parseTranscriptionResult(TranscriptionResult result) throws Exception {
        List<SubtitleSegment> segments = new ArrayList<>();
        
        // Get results from the task
        List<TranscriptionTaskResult> taskResults = result.getResults();
        if (taskResults == null || taskResults.isEmpty()) {
            log.warn("No task results found");
            return segments;
        }
        
        // Process first result (we only submitted one audio file)
        TranscriptionTaskResult taskResult = taskResults.get(0);
        
        if (taskResult.getSubTaskStatus() != TaskStatus.SUCCEEDED) {
            throw new RuntimeException("Subtask failed with status: " + taskResult.getSubTaskStatus() 
                + ", message: " + taskResult.getMessage());
        }
        
        String transcriptionUrl = taskResult.getTranscriptionUrl();
        if (transcriptionUrl == null || transcriptionUrl.isEmpty()) {
            throw new RuntimeException("Transcription URL is empty");
        }
        
        log.info("Downloading transcription result from: {}", transcriptionUrl);
        
        // Download JSON result
        String jsonContent;
        try (InputStream in = new URL(transcriptionUrl).openStream()) {
            jsonContent = new String(in.readAllBytes(), "UTF-8");
        }
        
        log.debug("Transcription JSON: {}", jsonContent);
        
        // Parse JSON
        JsonNode root = objectMapper.readTree(jsonContent);
        
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
