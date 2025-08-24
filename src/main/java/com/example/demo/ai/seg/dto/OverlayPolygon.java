package com.example.demo.ai.seg.dto;

import java.util.List;

public record OverlayPolygon(
    String label,
    String labelZh,
    double confidence,
    List<Point> points
) implements OverlayShape {}