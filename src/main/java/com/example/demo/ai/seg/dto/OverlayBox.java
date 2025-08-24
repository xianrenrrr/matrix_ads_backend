package com.example.demo.ai.seg.dto;

public record OverlayBox(
    String label,
    String labelZh,
    double confidence,
    double x,
    double y,
    double w,
    double h
) implements OverlayShape {}