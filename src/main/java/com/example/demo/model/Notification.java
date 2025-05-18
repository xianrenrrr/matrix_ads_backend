package com.example.demo.model;

public class Notification {
    private String type; // e.g., "video_approved"
    private String message;
    private long timestamp;
    private boolean read;

    public Notification() {}

    public Notification(String type, String message, long timestamp, boolean read) {
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
        this.read = read;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
