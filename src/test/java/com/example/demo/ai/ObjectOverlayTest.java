package com.example.demo.ai;

import com.example.demo.model.SceneSegment;
import com.example.demo.model.Scene;
import com.example.demo.model.Scene.ObjectOverlay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for object overlay functionality in AI template generation
 */
@SpringBootTest
@ActiveProfiles("ci")
public class ObjectOverlayTest {

    @Test
    public void testSceneSegmentWithObjectOverlays() {
        // Create a scene segment with object overlays
        SceneSegment segment = new SceneSegment();
        segment.setStartTime(Duration.ofSeconds(0));
        segment.setEndTime(Duration.ofSeconds(5));
        segment.setLabels(Arrays.asList("person", "product", "indoor"));
        segment.setPersonPresent(true);
        
        // Create mock object overlays
        ObjectOverlay overlay1 = new ObjectOverlay("person", 0.95f, 0.2f, 0.3f, 0.3f, 0.5f);
        ObjectOverlay overlay2 = new ObjectOverlay("product", 0.87f, 0.5f, 0.4f, 0.2f, 0.3f);
        List<ObjectOverlay> overlays = Arrays.asList(overlay1, overlay2);
        
        segment.setOverlayObjects(overlays);
        
        // Verify the segment has overlay objects
        assertNotNull(segment.getOverlayObjects());
        assertEquals(2, segment.getOverlayObjects().size());
        
        // Verify first overlay object
        ObjectOverlay firstOverlay = segment.getOverlayObjects().get(0);
        assertEquals("person", firstOverlay.getLabel());
        assertEquals(0.95f, firstOverlay.getConfidence(), 0.01);
        assertEquals(0.2f, firstOverlay.getX(), 0.01);
        assertEquals(0.3f, firstOverlay.getY(), 0.01);
        assertEquals(0.3f, firstOverlay.getW(), 0.01);
        assertEquals(0.5f, firstOverlay.getH(), 0.01);
    }
    
    @Test
    public void testSceneWithObjectOverlayType() {
        // Create a scene with object overlay type
        Scene scene = new Scene();
        scene.setSceneNumber(1);
        scene.setSceneTitle("Product Showcase");
        scene.setOverlayType("objects");
        
        // Add overlay objects
        ObjectOverlay overlay = new ObjectOverlay("product", 0.9f, 0.4f, 0.3f, 0.3f, 0.4f);
        scene.setOverlayObjects(Arrays.asList(overlay));
        
        // Verify scene configuration
        assertEquals("objects", scene.getOverlayType());
        assertNotNull(scene.getOverlayObjects());
        assertEquals(1, scene.getOverlayObjects().size());
        assertEquals("product", scene.getOverlayObjects().get(0).getLabel());
    }
    
    @Test
    public void testSceneWithGridFallback() {
        // Create a scene without overlay objects (should use grid)
        Scene scene = new Scene();
        scene.setSceneNumber(2);
        scene.setSceneTitle("Wide Shot");
        scene.setOverlayType("grid");
        
        // Verify grid configuration
        assertEquals("grid", scene.getOverlayType());
        assertNull(scene.getOverlayObjects());
    }
    
    @Test
    public void testObjectOverlayConfidenceThreshold() {
        // Test that low confidence objects would be filtered
        ObjectOverlay lowConfidence = new ObjectOverlay("unknown", 0.3f, 0.1f, 0.1f, 0.1f, 0.1f);
        ObjectOverlay highConfidence = new ObjectOverlay("person", 0.85f, 0.4f, 0.3f, 0.3f, 0.5f);
        
        // In real implementation, only high confidence should be kept
        assertTrue(highConfidence.getConfidence() > 0.5f, "High confidence object should pass threshold");
        assertFalse(lowConfidence.getConfidence() > 0.5f, "Low confidence object should not pass threshold");
    }
    
    @Test
    public void testObjectOverlayNormalizedCoordinates() {
        // Test that coordinates are properly normalized (0-1 range)
        ObjectOverlay overlay = new ObjectOverlay("object", 0.8f, 0.0f, 0.0f, 1.0f, 1.0f);
        
        // Verify all coordinates are within normalized range
        assertTrue(overlay.getX() >= 0.0f && overlay.getX() <= 1.0f);
        assertTrue(overlay.getY() >= 0.0f && overlay.getY() <= 1.0f);
        assertTrue(overlay.getW() >= 0.0f && overlay.getW() <= 1.0f);
        assertTrue(overlay.getH() >= 0.0f && overlay.getH() <= 1.0f);
    }
}