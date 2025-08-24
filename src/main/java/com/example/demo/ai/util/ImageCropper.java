package com.example.demo.ai.util;

import com.example.demo.ai.seg.dto.OverlayBox;
import com.example.demo.ai.seg.dto.OverlayPolygon;
import com.example.demo.ai.seg.dto.OverlayShape;
import com.example.demo.ai.seg.dto.Point;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class ImageCropper {
    
    public static byte[] crop(String keyframeUrl, OverlayShape shape) {
        try {
            // Download image
            BufferedImage image = ImageIO.read(new URL(keyframeUrl));
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            
            // Calculate crop bounds
            Rectangle bounds;
            
            if (shape instanceof OverlayBox box) {
                int x = (int) (box.x() * imgWidth);
                int y = (int) (box.y() * imgHeight);
                int w = (int) (box.w() * imgWidth);
                int h = (int) (box.h() * imgHeight);
                bounds = new Rectangle(x, y, w, h);
            } else if (shape instanceof OverlayPolygon polygon) {
                bounds = getPolygonBounds(polygon.points(), imgWidth, imgHeight);
            } else {
                throw new IllegalArgumentException("Unsupported shape type");
            }
            
            // Ensure bounds are within image
            bounds.x = Math.max(0, bounds.x);
            bounds.y = Math.max(0, bounds.y);
            bounds.width = Math.min(bounds.width, imgWidth - bounds.x);
            bounds.height = Math.min(bounds.height, imgHeight - bounds.y);
            
            // Crop the image
            BufferedImage cropped = image.getSubimage(
                bounds.x, bounds.y, bounds.width, bounds.height
            );
            
            // Convert to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(cropped, "jpg", baos);
            return baos.toByteArray();
            
        } catch (IOException e) {
            System.err.println("Failed to crop image: " + e.getMessage());
            return new byte[0];
        }
    }
    
    private static Rectangle getPolygonBounds(List<Point> points, int imgWidth, int imgHeight) {
        if (points.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }
        
        double minX = 1.0, minY = 1.0, maxX = 0.0, maxY = 0.0;
        
        for (Point p : points) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        
        int x = (int) (minX * imgWidth);
        int y = (int) (minY * imgHeight);
        int w = (int) ((maxX - minX) * imgWidth);
        int h = (int) ((maxY - minY) * imgHeight);
        
        return new Rectangle(x, y, w, h);
    }
}