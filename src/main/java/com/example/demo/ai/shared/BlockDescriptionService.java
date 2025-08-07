package com.example.demo.ai.shared;

import java.util.Map;

public interface BlockDescriptionService {
    Map<String, String> describeBlocks(Map<String, String> blockImageUrls);
    
    Map<String, String> describeBlocks(Map<String, String> blockImageUrls, String language);
}