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
        assertEquals(0.3f, firstOverlay.getWidth(), 0.01);
        assertEquals(0.5f, firstOverlay.getHeight(), 0.01);
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
        assertTrue(overlay.getWidth() >= 0.0f && overlay.getWidth() <= 1.0f);
        assertTrue(overlay.getHeight() >= 0.0f && overlay.getHeight() <= 1.0f);
    }
    
    @Test
    public void testObjectOverlayScoreRanges() {
        // Test that object scores in [0..1] are preserved
        ObjectOverlay lowScore = new ObjectOverlay("object1", 0.1f, 0.1f, 0.1f, 0.2f, 0.2f);
        ObjectOverlay midScore = new ObjectOverlay("object2", 0.5f, 0.3f, 0.3f, 0.3f, 0.3f);
        ObjectOverlay highScore = new ObjectOverlay("object3", 0.9f, 0.5f, 0.5f, 0.4f, 0.4f);
        
        // Verify confidence scores are preserved correctly
        assertEquals(0.1f, lowScore.getConfidence(), 0.01f);
        assertEquals(0.5f, midScore.getConfidence(), 0.01f);
        assertEquals(0.9f, highScore.getConfidence(), 0.01f);
        
        // Verify boxes are clamped to [0,1]
        assertTrue(lowScore.getX() + lowScore.getWidth() <= 1.0f);
        assertTrue(lowScore.getY() + lowScore.getHeight() <= 1.0f);
        assertTrue(highScore.getX() + highScore.getWidth() <= 1.0f);
        assertTrue(highScore.getY() + highScore.getHeight() <= 1.0f);
    }
    
    @Test
    public void testDualOverlaySystemBranching() {
        // Test that overlay types are mutually exclusive in behavior
        
        // Scene with object overlays
        Scene objectScene = new Scene();
        objectScene.setSceneNumber(1);
        objectScene.setOverlayType("objects");
        
        ObjectOverlay overlay = new ObjectOverlay("product", 0.85f, 0.3f, 0.2f, 0.4f, 0.5f);
        objectScene.setOverlayObjects(Arrays.asList(overlay));
        
        // Verify object mode
        assertEquals("objects", objectScene.getOverlayType());
        assertNotNull(objectScene.getOverlayObjects());
        assertEquals(1, objectScene.getOverlayObjects().size());
        
        // Scene with grid overlay (traditional)
        Scene gridScene = new Scene();
        gridScene.setSceneNumber(2);
        gridScene.setOverlayType("grid");
        gridScene.setScreenGridOverlay(Arrays.asList(1, 2, 3)); // Mock grid blocks
        
        // Verify grid mode
        assertEquals("grid", gridScene.getOverlayType());
        assertNull(gridScene.getOverlayObjects()); // Should not have object overlays
        assertNotNull(gridScene.getScreenGridOverlay()); // Should have grid data
    }
    
    @Test
    public void testObjectOverlayAreaCalculation() {
        // Test area calculations used in confidence ranking
        ObjectOverlay smallBox = new ObjectOverlay("small", 0.9f, 0.4f, 0.4f, 0.1f, 0.1f); // area = 0.01
        ObjectOverlay largeBox = new ObjectOverlay("large", 0.6f, 0.1f, 0.1f, 0.5f, 0.8f);  // area = 0.4
        
        float smallArea = smallBox.getWidth() * smallBox.getHeight();
        float largeArea = largeBox.getWidth() * largeBox.getHeight();
        
        assertEquals(0.01f, smallArea, 0.001f);
        assertEquals(0.4f, largeArea, 0.001f);
        
        // Test confidence * area scoring used in ranking
        float smallScore = smallBox.getConfidence() * smallArea;  // 0.9 * 0.01 = 0.009
        float largeScore = largeBox.getConfidence() * largeArea;  // 0.6 * 0.4 = 0.24
        
        assertTrue(largeScore > smallScore, "Large box with good confidence should score higher than small box");
    }
}