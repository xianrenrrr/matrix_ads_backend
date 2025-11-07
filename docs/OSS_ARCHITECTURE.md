# OSS Storage Architecture

## Overview
All Alibaba Cloud OSS (Object Storage Service) operations are centralized in `AlibabaOssStorageService` to ensure consistency, maintainability, and proper error handling.

## Centralized Service: AlibabaOssStorageService

### Upload Operations
All file uploads go through the centralized service:

```java
// Upload from InputStream
String url = ossStorageService.uploadFile(inputStream, objectKey, contentType);

// Upload from File
String url = ossStorageService.uploadFile(file, objectKey, contentType);

// Upload video with thumbnail (specialized)
UploadResult result = ossStorageService.uploadVideoWithThumbnail(multipartFile, userId, videoId);
```

### Download Operations
All file downloads use centralized methods:

```java
// Download single file
File tempFile = ossStorageService.downloadToTempFile(ossUrl, "prefix-", ".mp4");

// Download multiple files (batch)
List<File> tempFiles = ossStorageService.downloadMultipleToTempFiles(ossUrls, "prefix-", ".mp4");
```

### Access Control
OSS bucket is **PRIVATE** - all access requires signed URLs:

```java
// Generate signed URL (default 15 minutes)
String signedUrl = ossStorageService.generateSignedUrl(ossUrl);

// Generate signed URL with custom expiration
String signedUrl = ossStorageService.generateSignedUrl(ossUrl, 2, TimeUnit.HOURS);
```

### Delete Operations
```java
// Delete single object
boolean deleted = ossStorageService.deleteObjectByUrl(ossUrl);

// Delete by prefix (bulk delete)
int count = ossStorageService.deleteByPrefix("videos/userId/");
```

## Usage by Component

### VideoDaoImpl
- **Upload**: Uses `uploadVideoWithThumbnail()` for video uploads
- **Download**: Uses `downloadToTempFile()` for duration extraction
- **Access**: Uses `generateSignedUrl()` for streaming

### VideoCompilationServiceImpl
- **Download**: Uses `downloadMultipleToTempFiles()` to download scene videos for compilation
- **Upload**: Uses `uploadFile()` to upload compiled videos
- **Processing**: All FFmpeg operations use local files downloaded via OSS service

### SceneSubmissionController
- **Upload**: Uses `uploadVideoWithThumbnail()` for scene video uploads
- **Access**: Uses `generateSignedUrl()` for video playback

## Benefits of Centralization

1. **Single Source of Truth**: All OSS logic in one place
2. **Consistent Error Handling**: Unified error messages and logging
3. **Easy Maintenance**: Changes to OSS logic only need to be made once
4. **Performance Optimization**: Can add caching, retry logic, or connection pooling in one place
5. **Security**: Centralized access control and credential management
6. **Testing**: Easier to mock and test OSS operations

## Best Practices

### DO ✅
- Always use `ossStorageService` methods for OSS operations
- Use `downloadToTempFile()` when FFmpeg needs to process videos
- Clean up temp files in `finally` blocks
- Use appropriate expiration times for signed URLs (2 hours for processing, 15 min for viewing)

### DON'T ❌
- Don't create OSS clients directly in other services
- Don't manually construct signed URLs
- Don't use signed URLs directly in FFmpeg concat files (download first)
- Don't forget to delete temp files after processing

## Complete Video Compilation Flow with Subtitles

```
Manager clicks "Publish" button
         ↓
┌────────────────────────────────────────────────────────────┐
│ 1. GET SCENE VIDEOS FROM FIRESTORE                         │
│    • Query submittedVideos/{compositeId}                   │
│    • Extract scene URLs from scenes map                    │
│    • Example: ["oss://scene1.mp4", "oss://scene2.mp4"]    │
└────────────────────┬───────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 2. DOWNLOAD ALL SCENE VIDEOS (Centralized OSS)            │
│    ossStorageService.downloadMultipleToTempFiles()         │
│                                                            │
│    For each OSS URL:                                       │
│    • Generate signed URL (2 hour expiration)              │
│    • Download to /tmp/scene-0-xxx.mp4                     │
│    • Download to /tmp/scene-1-xxx.mp4                     │
│    • Download to /tmp/scene-2-xxx.mp4                     │
│                                                            │
│    Result: List<File> localVideoFiles                     │
└────────────────────┬───────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 3. GENERATE SUBTITLE FILE (SRT)                           │
│    SubtitleBurningService.generateSrtFile()                │
│                                                            │
│    Input: Template scenes with subtitleSegments           │
│    Output: /tmp/subtitles_xxx.srt                         │
│                                                            │
│    Example SRT content:                                    │
│    1                                                       │
│    00:00:00,000 --> 00:00:05,000                          │
│    泉州想贴车衣的直接来                                   │
│                                                            │
│    2                                                       │
│    00:00:05,000 --> 00:00:10,000                          │
│    专业贴膜服务                                            │
└────────────────────┬───────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 4. CREATE CONCAT LIST FILE                                │
│    Create /tmp/concat-xxx.txt with LOCAL paths:           │
│                                                            │
│    file '/tmp/scene-0-xxx.mp4'                            │
│    file '/tmp/scene-1-xxx.mp4'                            │
│    file '/tmp/scene-2-xxx.mp4'                            │
└────────────────────┬───────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 5. RUN FFMPEG TO COMPILE + BURN SUBTITLES                 │
│    ffmpeg -y                                               │
│      -f concat -safe 0 -i /tmp/concat-xxx.txt             │
│      -filter_complex "[0:v]subtitles=/tmp/subs.srt:       │
│        force_style='FontSize=24,                           │
│                     PrimaryColour=&HFFFFFFFF,              │
│                     OutlineColour=&HFF000000,              │
│                     Outline=2,                             │
│                     Alignment=2'[v]"                       │
│      -map [v] -map 0:a                                    │
│      -c:v libx264 -preset veryfast -crf 23                │
│      -c:a aac -b:a 192k                                   │
│      -movflags +faststart                                  │
│      /tmp/compiled-xxx.mp4                                │
│                                                            │
│    Result: /tmp/compiled-xxx.mp4 (with burned subtitles!) │
└────────────────────┬───────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 6. UPLOAD COMPILED VIDEO TO OSS (Centralized)             │
│    ossStorageService.uploadFile()                          │
│                                                            │
│    Upload to:                                              │
│    videos/{userId}/{compositeId}/compiled_subtitled.mp4   │
│                                                            │
│    Returns: OSS URL of final video                         │
└────────────────────┬───────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 7. CLEANUP TEMP FILES                                     │
│    Delete all temporary files:                            │
│    • /tmp/scene-0-xxx.mp4                                 │
│    • /tmp/scene-1-xxx.mp4                                 │
│    • /tmp/scene-2-xxx.mp4                                 │
│    • /tmp/concat-xxx.txt                                  │
│    • /tmp/subtitles_xxx.srt                               │
│    • /tmp/compiled-xxx.mp4                                │
└────────────────────┬───────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 8. UPDATE DATABASE                                        │
│    • Save CompiledVideo record                            │
│    • Update submittedVideo status = "published"           │
│    • Set compiledVideoUrl                                 │
│    • Send notification to creator                         │
└────────────────────────────────────────────────────────────┘
```

### Why Download First?

**Problem with Direct URL Access:**
- Signed URLs contain query parameters (`?Expires=...&Signature=...`)
- FFmpeg's concat demuxer can fail with complex URLs
- Network latency during processing
- Unreliable across different FFmpeg versions

**Solution - Download First:**
- ✅ FFmpeg works best with local file paths
- ✅ No URL escaping issues
- ✅ No network timeouts during processing
- ✅ Consistent, reliable behavior
- ✅ Faster processing (no network I/O during concat)

### Code Example

```java
// 1. Download scene videos using centralized service
List<File> localVideos = ossStorageService.downloadMultipleToTempFiles(
    sceneUrls, "scene-", ".mp4"
);

try {
    // 2. Generate SRT file
    String srtPath = subtitleBurningService.generateSrtFile(scenes);
    
    // 3. Process with FFmpeg (uses local files)
    File compiledVideo = ffmpegCompileWithSubtitles(localVideos, srtPath);
    
    // 4. Upload result using centralized service
    String url = ossStorageService.uploadFile(
        compiledVideo, 
        "videos/userId/compiled_subtitled.mp4", 
        "video/mp4"
    );
    
    return url;
} finally {
    // 5. Clean up temp files
    for (File f : localVideos) {
        f.delete();
    }
}
```

## Configuration

OSS service is configured via environment variables:
- `ALIBABA_CLOUD_ACCESS_KEY_ID`
- `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
- `alibaba.oss.bucket` (default: xpectra)
- `alibaba.oss.endpoint` (default: oss-cn-shanghai.aliyuncs.com)
- `alibaba.oss.enabled` (must be true)
