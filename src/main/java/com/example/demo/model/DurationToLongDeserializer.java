package com.example.demo.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Duration;

/**
 * Custom deserializer to handle Duration strings (like "PT0S", "PT32S") and convert them to long seconds.
 * Also handles regular int/long values.
 * Use this for long/Long fields.
 */
public class DurationToLongDeserializer extends JsonDeserializer<Long> {
    
    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        
        // If it's a Duration string (starts with "PT")
        if (value.startsWith("PT")) {
            try {
                Duration duration = Duration.parse(value);
                return duration.getSeconds();
            } catch (Exception e) {
                // If parsing fails, return 0
                return 0L;
            }
        }
        
        // Otherwise, try to parse as long
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
