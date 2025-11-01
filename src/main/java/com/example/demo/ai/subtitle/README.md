# Subtitle Extraction Services

This package provides subtitle extraction from videos using various AI services.

## Available Services

### 1. AzureVideoIndexerExtractor ⭐ RECOMMENDED
**Best accuracy for production use**

```java
@Autowired
private AzureVideoIndexerExtractor azureExtractor;

List<SubtitleSegment> subtitles = azureExtractor.extract(videoUrl);
Map<Integer, String> scriptLines = azureExtractor.groupByScenes(subtitles, scenes);
```

**Features:**
- Transcript extraction with word-level timing
- OCR for on-screen text
- Automatic language detection
- High accuracy for Chinese and English

**Setup:** See [AZURE_VIDEO_INDEXER_SETUP.md](../../../../../../../docs/AZURE_VIDEO_INDEXER_SETUP.md)

### 2. ASRSubtitleExtractor
**Good for budget-conscious projects**

```java
@Autowired
private ASRSubtitleExtractor asrExtractor;

List<SubtitleSegment> subtitles = asrExtractor.extract(videoUrl, "zh-CN");
```

**Features:**
- Audio transcript extraction
- Good accuracy for Chinese
- Lower cost than Azure

### 3. OCRSubtitleExtractor
**Not recommended - poor accuracy**

```java
@Autowired
private OCRSubtitleExtractor ocrExtractor;

List<SubtitleSegment> subtitles = ocrExtractor.extract(videoUrl);
```

**Issues:**
- Only extracts 2-3 segments from 100+ text elements
- Confuses UI elements with subtitles
- Requires complex filtering logic

## Quick Start

### 1. Configure Azure Video Indexer

Add to `application.properties`:
```properties
AZURE_VIDEO_INDEXER_ACCOUNT_ID=your-account-id
AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY=your-key
AZURE_VIDEO_INDEXER_LOCATION=trial
```

### 2. Use in Your Service

```java
@Service
public class YourService {
    
    @Autowired
    private AzureVideoIndexerExtractor azureExtractor;
    
    public void processVideo(String videoUrl) {
        // Extract subtitles
        List<SubtitleSegment> subtitles = azureExtractor.extract(videoUrl);
        
        // Use subtitles
        for (SubtitleSegment subtitle : subtitles) {
            System.out.printf("%s - %s: %s\n",
                formatTime(subtitle.getStartTimeMs()),
                formatTime(subtitle.getEndTimeMs()),
                subtitle.getText()
            );
        }
    }
}
```

### 3. Test

```bash
cd model_testing
export AZURE_VIDEO_INDEXER_ACCOUNT_ID="your-id"
export AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY="your-key"
java -cp "../matrix_ads_backend/target/classes:." AzureVideoIndexerTest
```

## Data Model

### SubtitleSegment

```java
public class SubtitleSegment {
    private long startTimeMs;    // Start time in milliseconds
    private long endTimeMs;      // End time in milliseconds
    private String text;         // Subtitle text
    private double confidence;   // Recognition confidence (0.0-1.0)
    
    // Convert to SRT format for FFmpeg
    public String toSRT(int sequenceNumber);
}
```

## Cost Comparison

| Service | Cost/Hour | Accuracy | Recommended |
|---------|-----------|----------|-------------|
| Azure Video Indexer | $9 | ⭐⭐⭐⭐⭐ | ✅ Yes |
| Alibaba ASR | $0.30 | ⭐⭐⭐ | Budget only |
| Alibaba OCR | $0.50 | ⭐⭐ | ❌ No |

## Documentation

- [Azure Video Indexer Setup](../../../../../../../docs/AZURE_VIDEO_INDEXER_SETUP.md)
- [Service Comparison](../../../../../../../docs/SUBTITLE_EXTRACTION_COMPARISON.md)
- [API Reference](https://learn.microsoft.com/en-us/rest/api/videoindexer/)

## Support

For issues or questions, see the documentation or contact your team lead.
