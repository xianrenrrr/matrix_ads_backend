package com.example.demo.ai.seg.dto;

public sealed interface OverlayShape permits OverlayBox, OverlayPolygon {
    String label();
    double confidence();
    String labelZh();
}