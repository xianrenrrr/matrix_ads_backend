package com.example.demo.ai.orchestrator;

import com.example.demo.model.SceneSegment;
import com.example.demo.model.Scene.ObjectOverlay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QualityGate assessment logic
 */
@SpringBootTest
@ActiveProfiles("ci")
public class QualityGateTest {
    
    @Autowired
    private QualityGate qualityGate;

    @Test
    public void testHasUsableObjects_NoObjects_ReturnsFalse() {
        // Given
        SceneSegment scene = new SceneSegment();
        scene.setOverlayObjects(Collections.emptyList());
        
        // When
        boolean result = qualityGate.hasUsableObjects(scene);
        
        // Then
        assertFalse(result, "Scene with no objects should fail quality gate");
    }
    
    @Test
    public void testHasUsableObjects_NullObjects_ReturnsFalse() {
        // Given
        SceneSegment scene = new SceneSegment();
        scene.setOverlayObjects(null);
        
        // When
        boolean result = qualityGate.hasUsableObjects(scene);
        
        // Then
        assertFalse(result, "Scene with null objects should fail quality gate");
    }

    @Test
    public void testHasUsableObjects_GoodQuality_ReturnsTrue() {
        // Given
        SceneSegment scene = new SceneSegment();
        
        // Create high confidence, adequately sized objects
        ObjectOverlay overlay1 = new ObjectOverlay("person", 0.85f, 0.2f, 0.3f, 0.3f, 0.4f); // area = 0.12
        ObjectOverlay overlay2 = new ObjectOverlay("product", 0.75f, 0.5f, 0.4f, 0.2f, 0.3f); // area = 0.06
        
        scene.setOverlayObjects(Arrays.asList(overlay1, overlay2));
        
        // When
        boolean result = qualityGate.hasUsableObjects(scene);
        
        // Then
        assertTrue(result, "Scene with high confidence and adequate size should pass quality gate");
    }

    @Test
    public void testHasUsableObjects_LowConfidence_ReturnsFalse() {
        // Given
        SceneSegment scene = new SceneSegment();
        
        // Create low confidence objects (below 0.6 threshold)
        ObjectOverlay overlay1 = new ObjectOverlay("object", 0.3f, 0.2f, 0.3f, 0.3f, 0.4f); // area = 0.12
        ObjectOverlay overlay2 = new ObjectOverlay("thing", 0.4f, 0.5f, 0.4f, 0.2f, 0.3f);   // area = 0.06
        
        scene.setOverlayObjects(Arrays.asList(overlay1, overlay2));
        
        // When
        boolean result = qualityGate.hasUsableObjects(scene);
        
        // Then
        assertFalse(result, "Scene with low confidence objects should fail quality gate");
    }

    @Test
    public void testHasUsableObjects_SmallObjects_ReturnsFalse() {
        // Given
        SceneSegment scene = new SceneSegment();
        
        // Create high confidence but very small objects (below 0.02 area threshold)
        ObjectOverlay overlay1 = new ObjectOverlay("tiny", 0.9f, 0.2f, 0.3f, 0.1f, 0.1f); // area = 0.01
        ObjectOverlay overlay2 = new ObjectOverlay("small", 0.8f, 0.5f, 0.4f, 0.05f, 0.2f); // area = 0.01
        
        scene.setOverlayObjects(Arrays.asList(overlay1, overlay2));
        
        // When
        boolean result = qualityGate.hasUsableObjects(scene);
        
        // Then
        assertFalse(result, "Scene with very small objects should fail quality gate");
    }

    @Test
    public void testAssessScenes_MixedQuality() {
        // Given
        SceneSegment goodScene = new SceneSegment();
        goodScene.setStartTime(Duration.ofSeconds(0));
        goodScene.setEndTime(Duration.ofSeconds(5));
        ObjectOverlay goodOverlay = new ObjectOverlay("person", 0.85f, 0.2f, 0.3f, 0.3f, 0.4f);
        goodScene.setOverlayObjects(Arrays.asList(goodOverlay));
        
        SceneSegment badScene1 = new SceneSegment();
        badScene1.setStartTime(Duration.ofSeconds(5));
        badScene1.setEndTime(Duration.ofSeconds(10));
        badScene1.setOverlayObjects(Collections.emptyList()); // No objects
        
        SceneSegment badScene2 = new SceneSegment(); 
        badScene2.setStartTime(Duration.ofSeconds(10));
        badScene2.setEndTime(Duration.ofSeconds(15));
        ObjectOverlay lowConfOverlay = new ObjectOverlay("object", 0.3f, 0.2f, 0.3f, 0.3f, 0.4f);
        badScene2.setOverlayObjects(Arrays.asList(lowConfOverlay)); // Low confidence
        
        List<SceneSegment> scenes = Arrays.asList(goodScene, badScene1, badScene2);
        
        // When
        QualityGate.QualityAssessment assessment = qualityGate.assessScenes(scenes);
        
        // Then
        assertEquals(3, assessment.totalScenes);
        assertEquals(1, assessment.goodQualityScenes);
        assertEquals(2, assessment.needsRefinementScenes);
        assertTrue(assessment.hasRefinementNeeds());
        assertEquals(2.0/3.0, assessment.getRefinementRate(), 0.01);
        
        assertEquals(2, assessment.scenesNeedingRefinement.size());
        assertTrue(assessment.scenesNeedingRefinement.contains(badScene1));
        assertTrue(assessment.scenesNeedingRefinement.contains(badScene2));
    }

    @Test
    public void testAssessScenes_AllGoodQuality() {
        // Given
        SceneSegment scene1 = new SceneSegment();
        ObjectOverlay overlay1 = new ObjectOverlay("person", 0.9f, 0.1f, 0.1f, 0.4f, 0.5f);
        scene1.setOverlayObjects(Arrays.asList(overlay1));
        
        SceneSegment scene2 = new SceneSegment();
        ObjectOverlay overlay2 = new ObjectOverlay("product", 0.8f, 0.3f, 0.2f, 0.3f, 0.4f);
        scene2.setOverlayObjects(Arrays.asList(overlay2));
        
        List<SceneSegment> scenes = Arrays.asList(scene1, scene2);
        
        // When
        QualityGate.QualityAssessment assessment = qualityGate.assessScenes(scenes);
        
        // Then
        assertEquals(2, assessment.totalScenes);
        assertEquals(2, assessment.goodQualityScenes);
        assertEquals(0, assessment.needsRefinementScenes);
        assertFalse(assessment.hasRefinementNeeds());
        assertEquals(0.0, assessment.getRefinementRate(), 0.01);
        assertTrue(assessment.scenesNeedingRefinement.isEmpty());
    }
}