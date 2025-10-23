package com.example.demo.ai.subtitle;

import com.alibaba.dashscope.audio.asr.transcription.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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
            Path tempVideo = Files.createTempFile("video_", ".mp4");
            // TODO: Download video from URL
            videoFile = tempVideo.toFile();
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
            throw new NoApiKeyException("AI_QWEN_API_KEY environment variable not set");
        }
        
        // Create transcription request
        Transcription transcription = new Transcription();
        
        // Build transcription parameters
        TranscriptionParam param = TranscriptionParam.builder()
            .model("paraformer-v2")  // Qwen ASR model
            .fileUrls(List.of(audioFile.toURI().toString()))
            .language(mapLanguageCode(language))  // "zh" or "en"
            .build();
        
        // Submit transcription task
        log.info("Submitting ASR transcription task...");
        TranscriptionResult result = transcription.asyncCall(param);
        
        // Get task ID
        String taskId = result.getTaskId();
        log.info("ASR task submitted. Task ID: {}", taskId);
        
        // Poll for completion
        TranscriptionQueryParam queryParam = TranscriptionQueryParam.builder()
            .taskId(taskId)
            .build();
        
        TranscriptionQueryResult queryResult = null;
        int maxAttempts = 60;  // Max 5 minutes (60 * 5 seconds)
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            Thread.sleep(5000);  // Wait 5 seconds between polls
            queryResult = transcription.wait(queryParam);
            
            String status = queryResult.getTaskStatus();
            log.info("ASR task status: {} (attempt {}/{})", status, attempt + 1, maxAttempts);
            
            if ("SUCCEEDED".equals(status)) {
                break;
            } else if ("FAILED".equals(status)) {
                throw new RuntimeException("ASR task failed: " + queryResult.getMessage());
            }
            
            attempt++;
        }
        
        if (queryResult == null || !"SUCCEEDED".equals(queryResult.getTaskStatus())) {
            throw new RuntimeException("ASR task timeout after " + maxAttempts + " attempts");
        }
        
        // Parse results
        log.info("ASR task completed successfully. Parsing results...");
        return parseASRResults(queryResult);
    }
    
    /**
     * Parse ASR results into SubtitleSegment list
     */
    private List<SubtitleSegment> parseASRResults(TranscriptionQueryResult result) {
        List<SubtitleSegment> segments = new ArrayList<>();
        
        try {
            // Get transcription results
            List<TranscriptionTaskResult> taskResults = result.getResults();
            
            if (taskResults == null || taskResults.isEmpty()) {
                log.warn("No transcription results found");
                return segments;
            }
            
            // Process each result
            for (TranscriptionTaskResult taskResult : taskResults) {
                String transcriptionUrl = taskResult.getTranscriptionUrl();
                log.info("Downloading transcription from: {}", transcriptionUrl);
                
                // Download and parse transcription JSON
                String jsonContent = downloadTranscriptionJson(transcriptionUrl);
                segments.addAll(parseTranscriptionJson(jsonContent));
            }
            
            log.info("Parsed {} subtitle segments", segments.size());
            
        } catch (Exception e) {
            log.error("Failed to parse ASR results", e);
        }
        
        return segments;
    }
    
    /**
     * Download transcription JSON from URL
     */
    private String downloadTranscriptionJson(String url) throws IOException {
        java.net.URL transcriptionUrl = new java.net.URL(url);
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(transcriptionUrl.openStream(), java.nio.charset.StandardCharsets.UTF_8)
        );
        
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();
        
        return content.toString();
    }
    
    /**
     * Parse transcription JSON into SubtitleSegment list
     * 
     * Expected format:
     * {
     *   "transcripts": [{
     *     "channel_id": 0,
     *     "sentences": [{
     *       "begin_time": 100,
     *       "end_time": 3820,
     *       "text": "Hello world, 这里是阿里巴巴语音实验室。",
     *       "words": [...]
     *     }]
     *   }]
     * }
     */
    private List<SubtitleSegment> parseTranscriptionJson(String jsonContent) {
        List<SubtitleSegment> segments = new ArrayList<>();
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonContent);
            
            // Get transcripts array
            com.fasterxml.jackson.databind.JsonNode transcripts = root.get("transcripts");
            if (transcripts == null || !transcripts.isArray()) {
                log.warn("No transcripts array found in JSON");
                return segments;
            }
            
            // Process each transcript (usually just one for single audio file)
            for (com.fasterxml.jackson.databind.JsonNode transcript : transcripts) {
                com.fasterxml.jackson.databind.JsonNode sentences = transcript.get("sentences");
                
                if (sentences == null || !sentences.isArray()) {
                    continue;
                }
                
                // Process each sentence
                for (com.fasterxml.jackson.databind.JsonNode sentence : sentences) {
                    long beginTime = sentence.get("begin_time").asLong();
                    long endTime = sentence.get("end_time").asLong();
                    String text = sentence.get("text").asText();
                    
                    // Create SubtitleSegment
                    SubtitleSegment segment = new SubtitleSegment();
                    segment.setStartTimeMs(beginTime);
                    segment.setEndTimeMs(endTime);
                    segment.setText(text);
                    segment.setConfidence(1.0);  // Qwen ASR doesn't provide confidence
                    
                    segments.add(segment);
                    
                    log.debug("Parsed segment: {}ms-{}ms: {}", beginTime, endTime, text);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to parse transcription JSON", e);
        }
        
        return segments;
    }
    
    /**
     * Map language code to Alibaba Cloud format
     */
    private String mapLanguageCode(String language) {
        if (language == null) {
            return "zh";  // Default to Chinese
        }
        
        // Map common formats to Alibaba Cloud format
        switch (language.toLowerCase()) {
            case "zh":
            case "zh-cn":
            case "zh-hans":
            case "chinese":
                return "zh";
            case "en":
            case "en-us":
            case "en-gb":
            case "english":
                return "en";
            default:
                log.warn("Unknown language code: {}, defaulting to zh", language);
                return "zh";
        }
    }
}
