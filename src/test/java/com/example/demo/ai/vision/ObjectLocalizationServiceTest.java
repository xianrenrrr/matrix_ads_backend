package com.example.demo.ai.vision;

import com.example.demo.ai.vision.ObjectLocalizationService.OverlayPolygon;
import com.example.demo.util.FirebaseCredentialsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectLocalizationServiceTest {
    
    private ObjectLocalizationService objectLocalizationService;
    
    @Mock
    private FirebaseCredentialsUtil firebaseCredentialsUtil;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectLocalizationService = new ObjectLocalizationService();
        
        // Configure test settings
        ReflectionTestUtils.setField(objectLocalizationService, "firebaseCredentialsUtil", firebaseCredentialsUtil);
        ReflectionTestUtils.setField(objectLocalizationService, "polygonsEnabled", true);
        ReflectionTestUtils.setField(objectLocalizationService, "maxShapes", 4);
        ReflectionTestUtils.setField(objectLocalizationService, "minArea", 0.02f);
        ReflectionTestUtils.setField(objectLocalizationService, "confidenceThreshold", 0.6f);
    }
    
    @Test
    void testDetectObjectPolygonsWithDisabledPolygons() {
        // Given
        ReflectionTestUtils.setField(objectLocalizationService, "polygonsEnabled", false);
        String imageUrl = "gs://bucket/image.jpg";
        
        // When
        List<OverlayPolygon> result = objectLocalizationService.detectObjectPolygons(imageUrl);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testDetectObjectPolygonsWithNullImageUrl() {
        // When
        List<OverlayPolygon> result = objectLocalizationService.detectObjectPolygons(null);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testDetectObjectPolygonsWithEmptyImageUrl() {
        // When
        List<OverlayPolygon> result = objectLocalizationService.detectObjectPolygons("");
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testOverlayPolygonConstructor() {
        // Given
        String label = "person";
        float confidence = 0.85f;
        List<OverlayPolygon.Point> points = Arrays.asList(
            new OverlayPolygon.Point(0.1f, 0.2f),
            new OverlayPolygon.Point(0.3f, 0.4f),
            new OverlayPolygon.Point(0.5f, 0.6f)
        );
        
        // When
        OverlayPolygon polygon = new OverlayPolygon(label, confidence, points);
        
        // Then
        assertEquals(label, polygon.getLabel());
        assertEquals(confidence, polygon.getConfidence(), 0.001f);
        assertEquals(3, polygon.getPoints().size());
        assertEquals(0.1f, polygon.getPoints().get(0).getX(), 0.001f);
        assertEquals(0.2f, polygon.getPoints().get(0).getY(), 0.001f);
    }
    
    @Test
    void testOverlayPolygonWithNullPoints() {
        // Given
        String label = "person";
        float confidence = 0.85f;
        
        // When
        OverlayPolygon polygon = new OverlayPolygon(label, confidence, null);
        
        // Then
        assertNotNull(polygon.getPoints());
        assertTrue(polygon.getPoints().isEmpty());
    }
    
    @Test
    void testOverlayPolygonSettersGetters() {
        // Given
        OverlayPolygon polygon = new OverlayPolygon();
        String label = "car";
        String labelLocalized = "汽车";
        float confidence = 0.75f;
        List<OverlayPolygon.Point> points = Arrays.asList(
            new OverlayPolygon.Point(0.0f, 0.0f),
            new OverlayPolygon.Point(1.0f, 0.0f),
            new OverlayPolygon.Point(1.0f, 1.0f),
            new OverlayPolygon.Point(0.0f, 1.0f)
        );
        
        // When
        polygon.setLabel(label);
        polygon.setLabelLocalized(labelLocalized);
        polygon.setConfidence(confidence);
        polygon.setPoints(points);
        
        // Then
        assertEquals(label, polygon.getLabel());
        assertEquals(labelLocalized, polygon.getLabelLocalized());
        assertEquals(confidence, polygon.getConfidence(), 0.001f);
        assertEquals(4, polygon.getPoints().size());
    }
    
    @Test
    void testPointConstructorAndGettersSetters() {
        // Given
        float x = 0.25f;
        float y = 0.75f;
        
        // When
        OverlayPolygon.Point point1 = new OverlayPolygon.Point(x, y);
        OverlayPolygon.Point point2 = new OverlayPolygon.Point();
        point2.setX(x);
        point2.setY(y);
        
        // Then
        assertEquals(x, point1.getX(), 0.001f);
        assertEquals(y, point1.getY(), 0.001f);
        assertEquals(x, point2.getX(), 0.001f);
        assertEquals(y, point2.getY(), 0.001f);
    }
    
    @Test
    void testCalculatePolygonArea() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = ObjectLocalizationService.class.getDeclaredMethod(
            "calculatePolygonArea", List.class);
        method.setAccessible(true);
        
        // Square with area 0.25 (0.5 x 0.5)
        List<OverlayPolygon.Point> square = Arrays.asList(
            new OverlayPolygon.Point(0.25f, 0.25f),
            new OverlayPolygon.Point(0.75f, 0.25f),
            new OverlayPolygon.Point(0.75f, 0.75f),
            new OverlayPolygon.Point(0.25f, 0.75f)
        );
        
        // When
        float area = (Float) method.invoke(objectLocalizationService, square);
        
        // Then
        assertEquals(0.25f, area, 0.001f);
    }
    
    @Test
    void testCalculatePolygonAreaWithTriangle() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = ObjectLocalizationService.class.getDeclaredMethod(
            "calculatePolygonArea", List.class);
        method.setAccessible(true);
        
        // Right triangle with area 0.5 (1.0 x 1.0 / 2)
        List<OverlayPolygon.Point> triangle = Arrays.asList(
            new OverlayPolygon.Point(0.0f, 0.0f),
            new OverlayPolygon.Point(1.0f, 0.0f),
            new OverlayPolygon.Point(0.0f, 1.0f)
        );
        
        // When
        float area = (Float) method.invoke(objectLocalizationService, triangle);
        
        // Then
        assertEquals(0.5f, area, 0.001f);
    }
    
    @Test
    void testClamp() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = ObjectLocalizationService.class.getDeclaredMethod(
            "clamp", float.class, float.class, float.class);
        method.setAccessible(true);
        
        // Test clamping
        assertEquals(0.0f, (Float) method.invoke(objectLocalizationService, -0.5f, 0.0f, 1.0f), 0.001f);
        assertEquals(0.5f, (Float) method.invoke(objectLocalizationService, 0.5f, 0.0f, 1.0f), 0.001f);
        assertEquals(1.0f, (Float) method.invoke(objectLocalizationService, 1.5f, 0.0f, 1.0f), 0.001f);
    }
}