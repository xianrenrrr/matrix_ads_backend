package com.example.demo.ai.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LabelCache {
    private final Map<String, String> cache;
    
    public LabelCache(int capacity) {
        this.cache = new LinkedHashMap<String, String>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > capacity;
            }
        };
    }
    
    public synchronized String get(String key) {
        return cache.get(key);
    }
    
    public synchronized void put(String key, String value) {
        cache.put(key, value);
    }
    
    public synchronized void clear() {
        cache.clear();
    }
}