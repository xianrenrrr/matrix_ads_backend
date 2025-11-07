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

## Example: Video Compilation Flow

```java
// 1. Download scene videos using centralized service
List<File> localVideos = ossStorageService.downloadMultipleToTempFiles(
    sceneUrls, "scene-", ".mp4"
);

try {
    // 2. Process with FFmpeg (uses local files)
    File compiledVideo = ffmpegProcess(localVideos);
    
    // 3. Upload result using centralized service
    String url = ossStorageService.uploadFile(
        compiledVideo, 
        "compiled/userId/video.mp4", 
        "video/mp4"
    );
    
    return url;
} finally {
    // 4. Clean up temp files
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
