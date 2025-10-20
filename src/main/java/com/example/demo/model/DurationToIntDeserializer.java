package com.example.demo.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Duration;

/**
 * Custom deserializer to handle Duration strings (like "PT0S") and convert them to int seconds.
 * Also handles regular int values.
 */
public class DurationToIntDeserializer extends JsonDeserializer<Integer> {
    
    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        
        if (value == null || value.isEmpty()) {
            return 0;
        }
        
        // If it's a Duration string (starts with "PT")
        if (value.startsWith("PT")) {
            try {
                Duration duration = Duration.parse(value);
                return (int) duration.getSeconds();
            } catch (Exception e) {
                // If parsing fails, return 0
                return 0;
            }
        }
        
        // Otherwise, try to parse as int
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
