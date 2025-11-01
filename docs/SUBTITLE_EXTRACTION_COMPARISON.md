# Subtitle Extraction Service Comparison

## Overview

We support three subtitle extraction methods:

| Service | Type | Accuracy | Cost | Best For |
|---------|------|----------|------|----------|
| **Azure Video Indexer** | Transcript + OCR | ⭐⭐⭐⭐⭐ | $9/hour | Production (recommended) |
| Alibaba Cloud ASR | Audio transcript | ⭐⭐⭐ | $0.30/hour | Budget audio-only |
| Alibaba Cloud OCR | On-screen text | ⭐⭐ | $0.50/hour | Not recommended |

## Azure Video Indexer (Recommended)

### Pros
- **Best accuracy** for both Chinese and English
- **Transcript + OCR** in one service
- Word-level timing precision
- Automatic language detection
- Additional features: faces, emotions, scenes

### Cons
- Higher cost ($9/hour)
- Requires Azure account
- 10-minute processing time for 10-minute video

### Setup
```properties
AZURE_VIDEO_INDEXER_ACCOUNT_ID=your-account-id
AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY=your-key
AZURE_VIDEO_INDEXER_LOCATION=trial
```

### Usage
```java
@Autowired
private AzureVideoIndexerExtractor azureExtractor;

List<SubtitleSegment> subtitles = azureExtractor.extract(videoUrl);
```

### When to Use
- Production applications
- High-quality subtitle requirements
- Videos with speech (not just on-screen text)
- Budget allows for quality

## Alibaba Cloud ASR

### Pros
- Good accuracy for Chinese
- Lower cost ($0.30/hour)
- Fast processing (real-time)

### Cons
- Audio only (no on-screen text)
- Requires clear audio
- Less accurate than Azure

### Setup
```properties
ALIBABA_CLOUD_ACCESS_KEY_ID=your-key-id
ALIBABA_CLOUD_ACCESS_KEY_SECRET=your-secret
```

### Usage
```java
@Autowired
private ASRSubtitleExtractor asrExtractor;

List<SubtitleSegment> subtitles = asrExtractor.extract(videoUrl, "zh-CN");
```

### When to Use
- Budget-conscious projects
- Videos with clear audio narration
- Chinese language content
- No on-screen text needed

## Alibaba Cloud OCR

### Pros
- Can detect on-screen text
- Lower cost ($0.50/hour)

### Cons
- **Poor accuracy** (only 2-3 subtitle segments from 139 text elements)
- Requires complex filtering logic
- Misses most subtitles
- Picks up UI elements instead of subtitles

### Issues Found
From testing with example.json:
- 286 total text elements detected
- Only 3 subtitle segments extracted
- Confused UI elements (Y=10: "福建.泉州") with subtitles
- Missed actual subtitles at Y=290-299
- Text length filtering helps but still unreliable

### Recommendation
**Do not use for production.** Use Azure Video Indexer or ASR instead.

## Cost Analysis

### Example: 100 hours of video per month

| Service | Monthly Cost | Quality | ROI |
|---------|-------------|---------|-----|
| Azure Video Indexer | $900 | Excellent | High - fewer errors, less manual correction |
| Alibaba ASR | $30 | Good | Medium - some manual correction needed |
| Alibaba OCR | $50 | Poor | Low - extensive manual correction required |

### Break-even Analysis

If manual correction costs $20/hour:
- Azure: $900 + $0 correction = $900
- ASR: $30 + $200 correction (10 hours) = $230
- OCR: $50 + $1000 correction (50 hours) = $1050

**Conclusion**: For production, Azure is worth the cost. For budget projects, use ASR.

## Migration Guide

### From Alibaba OCR to Azure Video Indexer

1. **Add Azure credentials** to application.properties
2. **Inject AzureVideoIndexerExtractor** in your service
3. **Replace extraction call**:
   ```java
   // Old
   List<SubtitleSegment> subtitles = ocrExtractor.extract(videoUrl);
   
   // New
   List<SubtitleSegment> subtitles = azureExtractor.extract(videoUrl);
   ```
4. **Test with sample videos**
5. **Monitor costs** in Azure portal

### Fallback Strategy

Use Azure as primary, ASR as fallback:

```java
List<SubtitleSegment> subtitles = new ArrayList<>();

// Try Azure first
if (azureExtractor != null) {
    try {
        subtitles = azureExtractor.extract(videoUrl);
        if (!subtitles.isEmpty()) {
            log.info("✅ Azure extracted {} segments", subtitles.size());
            return subtitles;
        }
    } catch (Exception e) {
        log.warn("Azure failed, trying ASR fallback: {}", e.getMessage());
    }
}

// Fallback to ASR
if (asrExtractor != null) {
    subtitles = asrExtractor.extract(videoUrl, language);
    log.info("✅ ASR extracted {} segments", subtitles.size());
}

return subtitles;
```

## Testing

### Test Azure Video Indexer
```bash
cd model_testing
export AZURE_VIDEO_INDEXER_ACCOUNT_ID="your-id"
export AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY="your-key"
export AZURE_VIDEO_INDEXER_LOCATION="trial"
javac -cp "../matrix_ads_backend/target/classes:." AzureVideoIndexerTest.java
java -cp "../matrix_ads_backend/target/classes:." AzureVideoIndexerTest
```

### Test with Custom Video
```bash
java -cp "../matrix_ads_backend/target/classes:." AzureVideoIndexerTest "https://your-video-url.mp4"
```

## Support

- Azure Video Indexer: https://www.videoindexer.ai
- Alibaba Cloud ASR: https://help.aliyun.com/product/30413.html
- Issues: Contact your team lead
