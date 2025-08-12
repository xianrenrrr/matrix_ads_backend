package com.example.demo.ai.orchestrator;

import com.example.demo.ai.template.SceneDetectionService;
import com.example.demo.model.SceneSegment;
import com.example.demo.model.Scene.ObjectOverlay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for VideoAnalysisOrchestrator to ensure proper delegation and future refinement logic
 */
@SpringBootTest
@ActiveProfiles("ci")
public class VideoAnalysisOrchestratorTest {

    @Autowired
    private VideoAnalysisOrchestrator orchestrator;

    @MockBean
    private SceneDetectionService sceneDetectionService;

    @Test
    public void testAnalyze_EmptyScenes_ReturnsEmpty() {
        // Given
        String gcsUri = "gs://test-bucket/video.mp4";
        when(sceneDetectionService.detectScenes(gcsUri)).thenReturn(Collections.emptyList());

        // When
        List<SceneSegment> result = orchestrator.analyze(gcsUri);

        // Then
        assertTrue(result.isEmpty(), "Should return empty list when no scenes detected");
        verify(sceneDetectionService, times(1)).detectScenes(gcsUri);
    }

    @Test
    public void testAnalyze_WithScenes_ReturnsSameResults() {
        // Given
        String gcsUri = "gs://test-bucket/video.mp4";
        
        SceneSegment scene1 = new SceneSegment();
        scene1.setStartTime(Duration.ofSeconds(0));
        scene1.setEndTime(Duration.ofSeconds(5));
        scene1.setLabels(Arrays.asList("person", "indoor"));
        scene1.setPersonPresent(true);
        
        SceneSegment scene2 = new SceneSegment();
        scene2.setStartTime(Duration.ofSeconds(5));
        scene2.setEndTime(Duration.ofSeconds(10));
        scene2.setLabels(Arrays.asList("product", "outdoor"));
        scene2.setPersonPresent(false);
        
        // Add object overlays to scene2
        ObjectOverlay overlay = new ObjectOverlay("product", 0.85f, 0.3f, 0.2f, 0.4f, 0.5f);
        scene2.setOverlayObjects(Arrays.asList(overlay));
        
        List<SceneSegment> mockScenes = Arrays.asList(scene1, scene2);
        when(sceneDetectionService.detectScenes(gcsUri)).thenReturn(mockScenes);

        // When
        List<SceneSegment> result = orchestrator.analyze(gcsUri);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(2, result.size(), "Should return same number of scenes");
        
        // Verify scene1 properties
        SceneSegment resultScene1 = result.get(0);
        assertEquals(Duration.ofSeconds(0), resultScene1.getStartTime());
        assertEquals(Duration.ofSeconds(5), resultScene1.getEndTime());
        assertTrue(resultScene1.isPersonPresent());
        assertEquals(Arrays.asList("person", "indoor"), resultScene1.getLabels());
        
        // Verify scene2 properties with overlays
        SceneSegment resultScene2 = result.get(1);
        assertEquals(Duration.ofSeconds(5), resultScene2.getStartTime());
        assertEquals(Duration.ofSeconds(10), resultScene2.getEndTime());
        assertFalse(resultScene2.isPersonPresent());
        assertEquals(Arrays.asList("product", "outdoor"), resultScene2.getLabels());
        
        assertNotNull(resultScene2.getOverlayObjects(), "Scene2 should have overlay objects");
        assertEquals(1, resultScene2.getOverlayObjects().size(), "Scene2 should have one overlay");
        
        ObjectOverlay resultOverlay = resultScene2.getOverlayObjects().get(0);
        assertEquals("product", resultOverlay.getLabel());
        assertEquals(0.85f, resultOverlay.getConfidence(), 0.01f);
        
        verify(sceneDetectionService, times(1)).detectScenes(gcsUri);
    }

    @Test
    public void testAnalyze_ExceptionHandling_ReturnsEmpty() {
        // Given
        String gcsUri = "gs://test-bucket/invalid.mp4";
        when(sceneDetectionService.detectScenes(gcsUri)).thenThrow(new RuntimeException("Video analysis failed"));

        // When
        List<SceneSegment> result = orchestrator.analyze(gcsUri);

        // Then
        assertTrue(result.isEmpty(), "Should return empty list on exception");
        verify(sceneDetectionService, times(1)).detectScenes(gcsUri);
    }
}