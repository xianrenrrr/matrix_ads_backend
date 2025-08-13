package com.example.demo.ai.guidance;

import com.example.demo.model.LegendItem;
import com.example.demo.model.Scene;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OverlayLegendService {
    
    private static final String[] COLORS = {
        "#FF3B30", "#0A84FF", "#34C759", "#FF9F0A", "#AF52DE",
        "#32ADE6", "#FF375F", "#5E5CE6", "#30D158", "#FFD60A"
    };

    public List<LegendItem> buildLegendFromObjects(Scene scene) {
        // scene.getOverlayObjects() is expected to exist
        var objs = Optional.ofNullable(scene.getOverlayObjects()).orElse(List.of());

        // Sort deterministically: confidence * area (w*h), desc
        var sorted = new ArrayList<>(objs);
        sorted.sort((a, b) -> Float.compare(
            (b.getConfidence() * Math.max(0f, b.getWidth()) * Math.max(0f, b.getHeight())),
            (a.getConfidence() * Math.max(0f, a.getWidth()) * Math.max(0f, a.getHeight()))
        ));

        var legend = new ArrayList<LegendItem>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            var o = sorted.get(i);
            var li = new LegendItem();
            li.setLabel(o.getLabel());
            li.setLabelLocalized(o.getLabelLocalized() != null ? o.getLabelLocalized() : o.getLabel());
            li.setConfidence(o.getConfidence());
            li.setColorHex(COLORS[i % COLORS.length]);
            legend.add(li);
        }
        
        // Reorder scene.overlayObjects to match legend order
        scene.setOverlayObjects(sorted);
        return legend;
    }
}