package com.example.demo.ai.services;

import com.example.demo.ai.services.OverlayLegendService.LegendItem;
import com.example.demo.ai.seg.dto.OverlayPolygonClass;
import com.example.demo.model.Scene;
import com.example.demo.model.Scene.ObjectOverlay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OverlayLegendServiceTest {
    
    private OverlayLegendService overlayLegendService;
    
    @BeforeEach
    void setUp() {
        overlayLegendService = new OverlayLegendService();
    }
    
    @Test
    void testBuildLegendWithNullScene() {
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(null, "zh-CN");
        
        // Then
        assertTrue(legend.isEmpty());
    }
    
    @Test
    void testBuildLegendWithNullOverlayType() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType(null);
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "zh-CN");
        
        // Then
        assertTrue(legend.isEmpty());
    }
    
    @Test
    void testBuildLegendWithGridType() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType("grid");
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "zh-CN");
        
        // Then
        assertTrue(legend.isEmpty());
    }
    
    @Test
    void testBuildPolygonLegend() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType("polygons");
        
        OverlayPolygonClass polygon1 = new OverlayPolygonClass();
        polygon1.setLabel("person");
        polygon1.setLabelLocalized("人");
        polygon1.setConfidence(0.85f);
        
        OverlayPolygonClass polygon2 = new OverlayPolygonClass();
        polygon2.setLabel("car");
        polygon2.setLabelLocalized("汽车");
        polygon2.setConfidence(0.92f);
        
        scene.setOverlayPolygons(Arrays.asList(polygon1, polygon2));
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "zh-CN");
        
        // Then
        assertEquals(2, legend.size());
        
        // First item should be RED
        assertEquals("人", legend.get(0).getLabelLocalized());
        assertEquals("person", legend.get(0).getLabel());
        assertEquals(0.85f, legend.get(0).getConfidence(), 0.001f);
        assertEquals("#FF3B30", legend.get(0).getColorHex()); // RED - first color
        
        // Second item should be BLUE
        assertEquals("汽车", legend.get(1).getLabelLocalized());
        assertEquals("car", legend.get(1).getLabel());
        assertEquals(0.92f, legend.get(1).getConfidence(), 0.001f);
        assertEquals("#0A84FF", legend.get(1).getColorHex()); // BLUE - second color
    }
    
    @Test
    void testBuildObjectLegend() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType("objects");
        
        ObjectOverlay object1 = new ObjectOverlay();
        object1.setLabel("product");
        object1.setLabelLocalized("产品");
        object1.setConfidence(0.78f);
        
        ObjectOverlay object2 = new ObjectOverlay();
        object2.setLabel("vehicle");
        object2.setLabelLocalized("车辆");
        object2.setConfidence(0.88f);
        
        scene.setOverlayObjects(Arrays.asList(object1, object2));
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "zh-CN");
        
        // Then
        assertEquals(2, legend.size());
        
        // First item should be RED
        assertEquals("产品", legend.get(0).getLabelLocalized());
        assertEquals("product", legend.get(0).getLabel());
        assertEquals(0.78f, legend.get(0).getConfidence(), 0.001f);
        assertEquals("#FF3B30", legend.get(0).getColorHex()); // RED - first color
        
        // Second item should be BLUE
        assertEquals("车辆", legend.get(1).getLabelLocalized());
        assertEquals("vehicle", legend.get(1).getLabel());
        assertEquals(0.88f, legend.get(1).getConfidence(), 0.001f);
        assertEquals("#0A84FF", legend.get(1).getColorHex()); // BLUE - second color
    }
    
    @Test
    void testBuildLegendWithoutLocalizedLabels() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType("objects");
        
        ObjectOverlay object = new ObjectOverlay();
        object.setLabel("person");
        object.setLabelLocalized(null); // No localized label
        object.setConfidence(0.85f);
        
        scene.setOverlayObjects(Arrays.asList(object));
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "zh-CN");
        
        // Then
        assertEquals(1, legend.size());
        assertEquals("person", legend.get(0).getLabelLocalized()); // Falls back to English
        assertEquals("person", legend.get(0).getLabel());
    }
    
    @Test
    void testColorPaletteOrdering() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType("objects");
        
        // Create 8 objects to test color cycling
        ObjectOverlay[] objects = new ObjectOverlay[8];
        for (int i = 0; i < 8; i++) {
            objects[i] = new ObjectOverlay();
            objects[i].setLabel("object" + i);
            objects[i].setConfidence(0.8f);
        }
        
        scene.setOverlayObjects(Arrays.asList(objects));
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "en");
        
        // Then
        assertEquals(8, legend.size());
        
        // Test first 7 colors match palette
        String[] expectedColors = {"#FF3B30", "#0A84FF", "#34C759", "#FF9F0A", "#AF52DE", "#32ADE6", "#FF375F"};
        for (int i = 0; i < 7; i++) {
            assertEquals(expectedColors[i], legend.get(i).getColorHex(),
                "Color at index " + i + " should match palette");
        }
        
        // 8th item should cycle back to first color (RED)
        assertEquals("#FF3B30", legend.get(7).getColorHex());
    }
    
    @Test
    void testGetColorPalette() {
        // When
        String[] palette = overlayLegendService.getColorPalette();
        
        // Then
        assertEquals(7, palette.length);
        assertEquals("#FF3B30", palette[0]); // First color is RED
        assertEquals("#0A84FF", palette[1]); // Second color is BLUE
        assertEquals("#34C759", palette[2]); // Third color is GREEN
    }
    
    @Test
    void testLegendItemConstructorAndGettersSetters() {
        // Given
        String labelLocalized = "人";
        String label = "person";
        float confidence = 0.85f;
        String colorHex = "#FF3B30";
        
        // When
        LegendItem item1 = new LegendItem(labelLocalized, label, confidence, colorHex);
        LegendItem item2 = new LegendItem();
        item2.setLabelLocalized(labelLocalized);
        item2.setLabel(label);
        item2.setConfidence(confidence);
        item2.setColorHex(colorHex);
        
        // Then
        assertEquals(labelLocalized, item1.getLabelLocalized());
        assertEquals(label, item1.getLabel());
        assertEquals(confidence, item1.getConfidence(), 0.001f);
        assertEquals(colorHex, item1.getColorHex());
        
        assertEquals(labelLocalized, item2.getLabelLocalized());
        assertEquals(label, item2.getLabel());
        assertEquals(confidence, item2.getConfidence(), 0.001f);
        assertEquals(colorHex, item2.getColorHex());
    }
    
    @Test
    void testBuildLegendWithEmptyPolygons() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType("polygons");
        scene.setOverlayPolygons(Arrays.asList()); // Empty list
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "zh-CN");
        
        // Then
        assertTrue(legend.isEmpty());
    }
    
    @Test
    void testBuildLegendWithEmptyObjects() {
        // Given
        Scene scene = new Scene();
        scene.setOverlayType("objects");
        scene.setOverlayObjects(Arrays.asList()); // Empty list
        
        // When
        List<LegendItem> legend = overlayLegendService.buildLegend(scene, "zh-CN");
        
        // Then
        assertTrue(legend.isEmpty());
    }
}