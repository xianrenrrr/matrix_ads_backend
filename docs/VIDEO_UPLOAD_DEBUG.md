# Video Upload Debugging Guide

## Issue
Manual template creation was failing during video upload with no clear error message. The logs showed:
```
[VIDEO-UPLOAD] Starting upload for videoId: 21f984bb-39d9-4212-9460-d55a17e8357d
```
Then the process stopped without any error or completion message.

## Root Cause
The video upload process had insufficient error logging, making it impossible to diagnose where the failure occurred. The process could fail at multiple points:
1. Saving MultipartFile to temp file
2. Uploading video to OSS
3. FFmpeg thumbnail extraction
4. Uploading thumbnail to OSS
5. FFprobe duration extraction
6. Saving video metadata to Firestore

Without detailed logging at each step, it was impossible to identify which step was failing.

## Solution
Added comprehensive logging and error handling throughout the video upload pipeline:

### 1. AlibabaOssStorageService.uploadVideoWithThumbnail()
- Added try-catch-finally block for proper resource cleanup
- Added detailed logging at each step:
  - Temp file creation and size
  - Video upload progress
  - FFmpeg thumbnail extraction with output capture
  - Thumbnail upload progress
- Capture and log FFmpeg stderr output when extraction fails
- Ensure temp files are always cleaned up in finally block

### 2. VideoDaoImpl.uploadAndSaveVideo()
- Wrapped entire method in try-catch for top-level error handling
- Added logging for:
  - File details (name, size)
  - Upload completion
  - Duration extraction steps
  - Firestore save operation
- Better error messages with exception class names

## Debugging Steps
When video upload fails, check logs for:

1. **File Transfer**: Look for "Saving video to temp file" and file size
2. **OSS Upload**: Look for "Uploading video to OSS" and success message
3. **FFmpeg Thumbnail**: Look for "Extracting thumbnail" and any FFmpeg errors
4. **Duration Extraction**: Look for "Downloading video for duration extraction"
5. **Firestore Save**: Look for "Saving video to Firestore"

## Common Issues

### FFmpeg Not Found
```
[OSS] ❌ FFmpeg failed with exit code: 127
```
**Solution**: Install FFmpeg on the server

### Video Format Not Supported
```
[OSS] ❌ FFmpeg failed with exit code: 1
FFmpeg output: Invalid data found when processing input
```
**Solution**: Check video codec and format compatibility

### OSS Credentials Invalid
```
[OSS] ❌ Upload failed: OSSException - Access denied
```
**Solution**: Verify ALIBABA_CLOUD_ACCESS_KEY_ID and SECRET are correct

### Firestore Not Available
```
[VIDEO-UPLOAD] ❌ Upload failed: IllegalStateException - Firestore is not available
```
**Solution**: Check Firebase credentials configuration

## Testing
To test the video upload flow:
1. Create a manual template with 1-2 scenes
2. Upload small test videos (< 10MB)
3. Check logs for each step completion
4. Verify video and thumbnail appear in OSS bucket
5. Verify video metadata saved to Firestore

## Related Files
- `AlibabaOssStorageService.java` - OSS upload and thumbnail extraction
- `VideoDaoImpl.java` - Video upload orchestration and Firestore save
- `ContentManager.java` - Manual template creation endpoint
