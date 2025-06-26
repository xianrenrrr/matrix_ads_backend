package com.example.demo.ai;

import java.util.Map;

public interface BlockDescriptionService {
    Map<String, String> describeBlocks(Map<String, String> blockImageUrls);
}