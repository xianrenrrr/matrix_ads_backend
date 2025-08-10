package com.example.demo.testsupport;

import com.example.demo.ai.comparison.EmbeddingService;
import com.example.demo.ai.scene.SceneAnalysisService;
import com.example.demo.ai.shared.BlockDescriptionService;
import com.example.demo.ai.shared.KeyframeExtractionService;
import com.example.demo.ai.shared.VideoSummaryService;
import com.example.demo.ai.template.BlockGridService;
import com.example.demo.ai.template.SceneDetectionService;
import com.example.demo.dao.*;
import com.example.demo.model.*;
import com.example.demo.service.*;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@TestConfiguration
public class CiTestConfig {


    @Bean
    @Primary
    public SceneDetectionService sceneDetectionService() {
        SceneDetectionService service = Mockito.mock(SceneDetectionService.class);
        
        // Create synthetic scene segments
        List<SceneSegment> scenes = Arrays.asList(
            createSceneSegment(0L, 5000L, Arrays.asList("indoor", "person"), true),
            createSceneSegment(5000L, 10000L, Arrays.asList("outdoor", "product"), false),
            createSceneSegment(10000L, 15000L, Arrays.asList("closeup", "person"), true)
        );
        
        when(service.detectScenes(anyString())).thenReturn(scenes);
        
        return service;
    }

    @Bean
    @Primary
    public KeyframeExtractionService keyframeExtractionService() {
        KeyframeExtractionService service = Mockito.mock(KeyframeExtractionService.class);
        when(service.extractKeyframe(anyString(), any(Duration.class), any(Duration.class)))
            .thenReturn("https://example.com/frame.jpg");
        return service;
    }

    @Bean
    @Primary
    public BlockGridService blockGridService() {
        BlockGridService service = Mockito.mock(BlockGridService.class);
        Map<String, String> blockGrid = new HashMap<>();
        for (int i = 1; i <= 9; i++) {
            blockGrid.put(String.valueOf(i), "https://example.com/block" + i + ".jpg");
        }
        when(service.createBlockGrid(anyString())).thenReturn(blockGrid);
        return service;
    }

    @Bean
    @Primary
    public BlockDescriptionService blockDescriptionService() {
        BlockDescriptionService service = Mockito.mock(BlockDescriptionService.class);
        Map<String, String> descriptions = new HashMap<>();
        for (int i = 1; i <= 9; i++) {
            descriptions.put(String.valueOf(i), "Block " + i + " description");
        }
        when(service.describeBlocks(any())).thenReturn(descriptions);
        return service;
    }

    @Bean
    @Primary
    public VideoSummaryService videoSummaryService() {
        VideoSummaryService service = Mockito.mock(VideoSummaryService.class);
        when(service.generateSummary(any(Video.class), anyList(), anyMap()))
            .thenReturn("Test video summary");
        return service;
    }

    @Bean
    @Primary
    public FirebaseStorageService firebaseStorageService() throws Exception {
        FirebaseStorageService service = Mockito.mock(FirebaseStorageService.class);
        FirebaseStorageService.UploadResult uploadResult = 
            new FirebaseStorageService.UploadResult("https://example.com/video.mp4", "https://example.com/thumb.jpg");
        when(service.uploadVideoWithThumbnail(any(MultipartFile.class), anyString(), anyString()))
            .thenReturn(uploadResult);
        return service;
    }

    @Bean
    @Primary
    public NotificationService notificationService() {
        NotificationService service = Mockito.mock(NotificationService.class);
        // No-op for notifications
        return service;
    }

    @Bean
    @Primary
    public SceneAnalysisService sceneAnalysisService() {
        SceneAnalysisService service = Mockito.mock(SceneAnalysisService.class);
        // SceneAnalysisService methods will be mocked per test as needed
        return service;
    }

    @Bean
    @Primary
    public EmbeddingService embeddingService() {
        EmbeddingService service = Mockito.mock(EmbeddingService.class);
        when(service.generateEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        return service;
    }

    @Bean
    @Primary
    public VideoCompilationService videoCompilationService() {
        VideoCompilationService service = Mockito.mock(VideoCompilationService.class);
        // VideoCompilationService methods will be mocked per test as needed
        return service;
    }

    @Bean
    @Primary
    public WorkflowAutomationService workflowAutomationService() {
        return Mockito.mock(WorkflowAutomationService.class);
    }


    @Bean
    @Primary
    public TemplateSubscriptionService templateSubscriptionService() {
        return Mockito.mock(TemplateSubscriptionService.class);
    }

    @Bean
    @Primary
    public I18nService i18nService() {
        // Use real I18nService since it has no external dependencies
        return new I18nService();
    }

    // DAO Mocks
    @Bean
    @Primary
    public TemplateDao templateDao() throws Exception {
        TemplateDao dao = Mockito.mock(TemplateDao.class);
        ManualTemplate template = createMockTemplate();
        when(dao.getTemplate(anyString())).thenReturn(template);
        when(dao.getAllTemplates()).thenReturn(Arrays.asList(template));
        return dao;
    }

    @Bean
    @Primary
    public SceneSubmissionDao sceneSubmissionDao() throws Exception {
        SceneSubmissionDao dao = Mockito.mock(SceneSubmissionDao.class);
        // Return mock scene submissions
        SceneSubmission submission = new SceneSubmission();
        submission.setId("scene123");
        submission.setVideoUrl("https://example.com/scene.mp4");
        submission.setSimilarityScore(85.0);
        submission.setStatus("approved");
        when(dao.findById(anyString())).thenReturn(submission);
        when(dao.findByTemplateIdAndUserId(anyString(), anyString())).thenReturn(Arrays.asList(submission));
        return dao;
    }

    @Bean
    @Primary
    public UserDao userDao() throws Exception {
        UserDao dao = Mockito.mock(UserDao.class);
        User user = new User();
        user.setId("user123");
        user.setEmail("test@example.com");
        user.setRole("content_creator");
        // UserDao methods will be mocked per test as needed
        return dao;
    }

    @Bean
    @Primary
    public VideoDao videoDao() throws Exception {
        VideoDao dao = Mockito.mock(VideoDao.class);
        Video video = new Video();
        video.setId("test123");
        video.setUrl("https://example.com/video.mp4");
        // VideoDao methods will be mocked per test as needed
        return dao;
    }

    @Bean
    @Primary
    public CompiledVideoDao compiledVideoDao() {
        return Mockito.mock(CompiledVideoDao.class);
    }


    @Bean
    @Primary
    public InviteDao inviteDao() {
        return Mockito.mock(InviteDao.class);
    }

    // Helper methods
    private SceneSegment createSceneSegment(Long startMs, Long endMs, 
                                           List<String> labels, boolean personPresent) {
        SceneSegment segment = new SceneSegment();
        segment.setStartTimeMs(startMs);
        segment.setEndTimeMs(endMs);
        segment.setLabels(labels);
        segment.setPersonPresent(personPresent);
        return segment;
    }

    private ManualTemplate createMockTemplate() {
        ManualTemplate template = new ManualTemplate();
        template.setId("template456");
        template.setTemplateTitle("Test Template");
        
        List<Scene> scenes = new ArrayList<>();
        Scene scene1 = new Scene();
        scene1.setSceneNumber(1);
        scene1.setSceneTitle("Opening Scene");
        scenes.add(scene1);
        
        Scene scene2 = new Scene();
        scene2.setSceneNumber(2);
        scene2.setSceneTitle("Product Display");
        scenes.add(scene2);
        
        template.setScenes(scenes);
        // ManualTemplate doesn't have setSubmittedVideos method
        return template;
    }


    private static Map<String, Object> createMockSubmittedVideo() {
        Map<String, Object> video = new HashMap<>();
        video.put("id", "user123_template456");
        video.put("templateId", "template456");
        video.put("uploadedBy", "user123");
        video.put("publishStatus", "pending");
        
        Map<String, Object> scenes = new HashMap<>();
        Map<String, Object> scene1 = new HashMap<>();
        scene1.put("sceneNumber", 1);
        scene1.put("status", "approved");
        scene1.put("sceneId", "scene123");
        scene1.put("similarityScore", 90.0);
        scenes.put("1", scene1);
        
        Map<String, Object> scene2 = new HashMap<>();
        scene2.put("sceneNumber", 2);
        scene2.put("status", "pending");
        scene2.put("sceneId", "scene456");
        scene2.put("similarityScore", 75.0);
        scenes.put("2", scene2);
        
        video.put("scenes", scenes);
        
        Map<String, Object> progress = new HashMap<>();
        progress.put("totalScenes", 2);
        progress.put("approved", 1);
        progress.put("pending", 1);
        progress.put("rejected", 0);
        progress.put("completionPercentage", 50);
        
        video.put("progress", progress);
        
        return video;
    }
}