package com.example.demo.testsupport;

import com.example.demo.ai.services.KeyframeExtractionService;
import com.example.demo.ai.providers.llm.VideoSummaryService;
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
    public KeyframeExtractionService keyframeExtractionService() {
        KeyframeExtractionService service = Mockito.mock(KeyframeExtractionService.class);
        when(service.extractKeyframe(anyString(), any(Duration.class), any(Duration.class)))
            .thenReturn("https://example.com/frame.jpg");
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
    public VideoCompilationService videoCompilationService() {
        VideoCompilationService service = Mockito.mock(VideoCompilationService.class);
        // VideoCompilationService methods will be mocked per test as needed
        return service;
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
    public CompiledVideoDao compiledVideoDao() throws Exception {
        CompiledVideoDao dao = Mockito.mock(CompiledVideoDao.class);
        when(dao.getCompletedVideoCountByUser(anyString())).thenReturn(5);
        when(dao.getPublishedVideoCountByUser(anyString())).thenReturn(2);
        return dao;
    }

    @Bean
    @Primary
    public SubmittedVideoDao submittedVideoDao() throws Exception {
        SubmittedVideoDao dao = Mockito.mock(SubmittedVideoDao.class);
        when(dao.getVideoCountByUser(anyString())).thenReturn(3);
        when(dao.getPublishedVideoCountByUser(anyString())).thenReturn(1);
        return dao;
    }


    @Bean
    @Primary
    public GroupDao GroupDao() {
        return Mockito.mock(GroupDao.class);
    }

    // Helper methods
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


}