package com.example.demo.ai.orchestrator;

import com.example.demo.model.SceneSegment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for full pass analysis functionality
 */
@SpringBootTest
@ActiveProfiles("ci")
public class FullPassAnalysisTest {

    @Autowired
    private VideoAnalysisOrchestrator orchestrator;

    @MockBean
    private VideoMetadataService videoMetadataService;

    @MockBean
    private FullPassAnalysisService fullPassAnalysisService;

    @Test
    public void testFullPassRule_ShortVideo_UsesFullPass() {
        // Given
        String gcsUri = "gs://test-bucket/short-video.mp4";
        int shortDuration = 60; // seconds, under 120s threshold
        
        when(videoMetadataService.getVideoDurationSeconds(gcsUri)).thenReturn(shortDuration);
        
        SceneSegment mockScene = new SceneSegment();
        List<SceneSegment> mockResults = Arrays.asList(mockScene);
        when(fullPassAnalysisService.performFullPassAnalysis(gcsUri)).thenReturn(mockResults);

        // When
        List<SceneSegment> result = orchestrator.analyze(gcsUri);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should return mock results from full pass");
        
        verify(videoMetadataService, times(1)).getVideoDurationSeconds(gcsUri);
        verify(fullPassAnalysisService, times(1)).performFullPassAnalysis(gcsUri);
    }

    @Test
    public void testFullPassRule_LongVideo_UsesMultiPass() {
        // Given
        String gcsUri = "gs://test-bucket/long-video.mp4";
        int longDuration = 300; // seconds, over 120s threshold
        
        when(videoMetadataService.getVideoDurationSeconds(gcsUri)).thenReturn(longDuration);

        // When
        List<SceneSegment> result = orchestrator.analyze(gcsUri);

        // Then
        verify(videoMetadataService, times(1)).getVideoDurationSeconds(gcsUri);
        verify(fullPassAnalysisService, never()).performFullPassAnalysis(any());
        
        // Should use coarse pass (mocked via SceneDetectionService in parent test)
        assertNotNull(result, "Result should not be null");
    }

    @Test
    public void testFullPassRule_ExactThreshold_UsesFullPass() {
        // Given
        String gcsUri = "gs://test-bucket/threshold-video.mp4";
        int thresholdDuration = 120; // exactly at threshold
        
        when(videoMetadataService.getVideoDurationSeconds(gcsUri)).thenReturn(thresholdDuration);
        when(fullPassAnalysisService.performFullPassAnalysis(gcsUri)).thenReturn(Collections.emptyList());

        // When
        List<SceneSegment> result = orchestrator.analyze(gcsUri);

        // Then
        verify(videoMetadataService, times(1)).getVideoDurationSeconds(gcsUri);
        verify(fullPassAnalysisService, times(1)).performFullPassAnalysis(gcsUri);
        
        assertTrue(result.isEmpty(), "Should handle empty results from full pass");
    }

    @Test 
    public void testFullPassRule_UnknownDuration_UsesMultiPass() {
        // Given
        String gcsUri = "gs://test-bucket/unknown-video.mp4";
        int unknownDuration = -1; // Unable to determine duration
        
        when(videoMetadataService.getVideoDurationSeconds(gcsUri)).thenReturn(unknownDuration);

        // When
        List<SceneSegment> result = orchestrator.analyze(gcsUri);

        // Then
        verify(videoMetadataService, times(1)).getVideoDurationSeconds(gcsUri);
        verify(fullPassAnalysisService, never()).performFullPassAnalysis(any());
        
        // Should fall back to multi-pass approach
        assertNotNull(result, "Result should not be null");
    }
}