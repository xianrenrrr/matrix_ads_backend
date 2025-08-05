package com.example.demo.service;

import com.example.demo.dao.CompiledVideoDao;
import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.model.CompiledVideo;
import com.example.demo.model.SceneSubmission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Video Compilation Service Implementation
 * Handles FFmpeg-based video compilation from approved scene submissions
 */
@Service
public class VideoCompilationServiceImpl implements VideoCompilationService {
    
    @Autowired
    private CompiledVideoDao compiledVideoDao;
    
    @Autowired
    private SceneSubmissionDao sceneSubmissionDao;
    
    @Autowired(required = false)
    private FirebaseStorageService firebaseStorageService;
    
    // Mock FFmpeg job tracking
    private final Map<String, CompilationJob> activeJobs = new HashMap<>();
    
    @Override
    public boolean triggerCompilation(String templateId, String userId, List<String> sceneSubmissionIds, String triggeredBy) {
        try {
            // Check if compilation already exists and is not failed
            CompiledVideo existing = compiledVideoDao.findByTemplateIdAndUserId(templateId, userId);
            if (existing != null && !"failed".equals(existing.getStatus())) {
                System.out.println("Compilation already exists for template " + templateId + " user " + userId);
                return false;
            }
            
            // Get scene submissions
            List<SceneSubmission> scenes = new ArrayList<>();
            double totalDuration = 0;
            
            for (String sceneId : sceneSubmissionIds) {
                SceneSubmission scene = sceneSubmissionDao.findById(sceneId);
                if (scene != null && "approved".equals(scene.getStatus())) {
                    scenes.add(scene);
                    if (scene.getDuration() != null) {
                        totalDuration += scene.getDuration();
                    }
                } else {
                    System.err.println("Scene " + sceneId + " is not approved or not found");
                    return false;
                }
            }
            
            if (scenes.isEmpty()) {
                System.err.println("No approved scenes found for compilation");
                return false;
            }
            
            // Create compilation job ID
            String jobId = "compile_" + UUID.randomUUID().toString();
            
            // Create compiled video record
            CompiledVideo compiledVideo = new CompiledVideo(templateId, userId, sceneSubmissionIds);
            compiledVideo.setCompiledBy(triggeredBy);
            compiledVideo.setCompilationJobId(jobId);
            compiledVideo.setTotalDuration(totalDuration);
            compiledVideo.setTotalScenes(scenes.size());
            compiledVideo.setStatus("compiling");
            
            // Set compilation settings
            Map<String, Object> settings = new HashMap<>();
            settings.put("resolution", "1080p");
            settings.put("format", "mp4");
            settings.put("quality", "high");
            settings.put("codec", "h264");
            compiledVideo.setCompilationSettings(settings);
            
            // Create scene mapping
            Map<Integer, String> sceneMapping = new HashMap<>();
            for (int i = 0; i < scenes.size(); i++) {
                sceneMapping.put(scenes.get(i).getSceneNumber(), scenes.get(i).getId());
            }
            compiledVideo.setSceneMapping(sceneMapping);
            
            // Save to database
            String compiledVideoId = compiledVideoDao.save(compiledVideo);
            compiledVideo.setId(compiledVideoId);
            
            // Start compilation process (mock for now)
            startMockCompilation(compiledVideo, scenes);
            
            System.out.println("Started compilation job " + jobId + " for " + scenes.size() + " scenes");
            return true;
            
        } catch (Exception e) {
            System.err.println("Error triggering compilation: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public CompiledVideo checkCompilationStatus(String compilationJobId) throws ExecutionException, InterruptedException {
        CompiledVideo video = compiledVideoDao.findByCompilationJobId(compilationJobId);
        
        if (video != null && "compiling".equals(video.getStatus())) {
            // Check mock job status
            CompilationJob job = activeJobs.get(compilationJobId);
            if (job != null) {
                if (job.isCompleted()) {
                    // Mark as completed
                    video.markCompleted();
                    video.setVideoUrl(job.getOutputUrl());
                    video.setThumbnailUrl(generateThumbnail(job.getOutputUrl()));
                    video.setFileSize(job.getOutputFileSize());
                    video.setResolution("1080p");
                    video.setFormat("mp4");
                    
                    compiledVideoDao.update(video);
                    activeJobs.remove(compilationJobId);
                    
                } else if (job.isFailed()) {
                    // Mark as failed
                    video.markFailed(job.getErrors());
                    compiledVideoDao.update(video);
                    activeJobs.remove(compilationJobId);
                }
            }
        }
        
        return video;
    }
    
    @Override
    public boolean retryCompilation(String compiledVideoId) throws ExecutionException, InterruptedException {
        CompiledVideo video = compiledVideoDao.findById(compiledVideoId);
        
        if (video != null && video.canRetry()) {
            video.incrementRetryCount();
            video.setStatus("compiling");
            
            // Generate new job ID
            String newJobId = "retry_" + UUID.randomUUID().toString();
            video.setCompilationJobId(newJobId);
            
            // Clear previous errors
            video.setCompilationErrors(null);
            
            compiledVideoDao.update(video);
            
            // Restart compilation process
            List<SceneSubmission> scenes = new ArrayList<>();
            for (String sceneId : video.getSceneSubmissionIds()) {
                SceneSubmission scene = sceneSubmissionDao.findById(sceneId);
                if (scene != null) {
                    scenes.add(scene);
                }
            }
            
            startMockCompilation(video, scenes);
            
            System.out.println("Retrying compilation " + compiledVideoId + " with job " + newJobId);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean cancelCompilation(String compilationJobId) {
        CompilationJob job = activeJobs.get(compilationJobId);
        if (job != null && !job.isCompleted()) {
            job.cancel();
            activeJobs.remove(compilationJobId);
            
            try {
                CompiledVideo video = compiledVideoDao.findByCompilationJobId(compilationJobId);
                if (video != null) {
                    video.setStatus("failed");
                    video.setCompilationErrors(Arrays.asList("Compilation cancelled by user"));
                    compiledVideoDao.update(video);
                }
                
                System.out.println("Cancelled compilation job " + compilationJobId);
                return true;
                
            } catch (Exception e) {
                System.err.println("Error updating cancelled compilation: " + e.getMessage());
            }
        }
        
        return false;
    }
    
    @Override
    public double getCompilationProgress(String compilationJobId) {
        CompilationJob job = activeJobs.get(compilationJobId);
        return job != null ? job.getProgress() : 0.0;
    }
    
    @Override
    public int cleanupOldCompilations(int daysOld) throws ExecutionException, InterruptedException {
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.DAY_OF_MONTH, -daysOld);
        Date cutoffDate = cutoff.getTime();
        
        List<CompiledVideo> oldFailed = compiledVideoDao.findByDateRange(new Date(0), cutoffDate);
        int cleanedUp = 0;
        
        for (CompiledVideo video : oldFailed) {
            if ("failed".equals(video.getStatus())) {
                compiledVideoDao.delete(video.getId());
                cleanedUp++;
            }
        }
        
        System.out.println("Cleaned up " + cleanedUp + " old failed compilations");
        return cleanedUp;
    }
    
    @Override
    public String generateThumbnail(String videoUrl) {
        // Mock thumbnail generation
        return videoUrl.replace(".mp4", "_thumb.jpg");
    }
    
    @Override
    public long getEstimatedCompilationTime(double totalDurationSeconds) {
        // Estimate: roughly 1:1 ratio for high quality encoding
        // Add 30 seconds base overhead
        return (long) (totalDurationSeconds + 30);
    }
    
    // Mock Compilation Implementation
    
    private void startMockCompilation(CompiledVideo video, List<SceneSubmission> scenes) {
        CompilationJob job = new CompilationJob(video.getCompilationJobId(), scenes);
        activeJobs.put(video.getCompilationJobId(), job);
        
        // Start mock compilation in background thread
        new Thread(() -> {
            try {
                job.execute();
            } catch (Exception e) {
                System.err.println("Mock compilation failed: " + e.getMessage());
                job.fail(Arrays.asList("Mock compilation error: " + e.getMessage()));
            }
        }).start();
    }
    
    // Mock Compilation Job Class
    private static class CompilationJob {
        private final String jobId;
        private final List<SceneSubmission> scenes;
        private double progress = 0.0;
        private boolean completed = false;
        private boolean failed = false;
        private boolean cancelled = false;
        private String outputUrl;
        private Long outputFileSize;
        private List<String> errors;
        
        public CompilationJob(String jobId, List<SceneSubmission> scenes) {
            this.jobId = jobId;
            this.scenes = scenes;
        }
        
        public void execute() throws InterruptedException {
            System.out.println("Starting mock compilation for " + scenes.size() + " scenes");
            
            // Simulate compilation process
            for (int i = 0; i < scenes.size() && !cancelled; i++) {
                Thread.sleep(2000); // Simulate processing time per scene
                progress = (double) (i + 1) / scenes.size() * 100;
                System.out.println("Compilation progress: " + progress + "%");
            }
            
            if (!cancelled) {
                // Simulate final processing
                Thread.sleep(3000);
                
                // Mock successful completion
                completed = true;
                progress = 100.0;
                
                // Generate mock output URL
                outputUrl = String.format("https://storage.googleapis.com/compiled-videos/%s_final.mp4", jobId);
                outputFileSize = 50_000_000L; // 50MB mock file size
                
                System.out.println("Mock compilation completed: " + outputUrl);
            }
        }
        
        public void cancel() {
            cancelled = true;
        }
        
        public void fail(List<String> errors) {
            this.failed = true;
            this.errors = errors;
        }
        
        public boolean isCompleted() { return completed; }
        public boolean isFailed() { return failed; }
        public double getProgress() { return progress; }
        public String getOutputUrl() { return outputUrl; }
        public Long getOutputFileSize() { return outputFileSize; }
        public List<String> getErrors() { return errors; }
    }
}