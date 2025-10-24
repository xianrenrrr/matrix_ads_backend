# Alibaba OSS Migration Guide

## Step 1: Add Configuration

Add these to your `application.properties` or environment variables:

```properties
# Enable Alibaba OSS (disable Firebase)
alibaba.oss.enabled=true
firebase.enabled=false

# OSS Configuration
alibaba.oss.bucket=xpectra
alibaba.oss.endpoint=oss-cn-shanghai.aliyuncs.com

# Alibaba Cloud Credentials (already configured for Video Recognition)
ALIBABA_CLOUD_ACCESS_KEY_ID=your_access_key_id
ALIBABA_CLOUD_ACCESS_KEY_SECRET=your_access_key_secret
```

## Step 2: Update Maven Dependencies

Already added to `pom.xml`:
```xml
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
</dependency>
```

Run: `mvn clean install`

## Step 3: Update Code References

### Option A: Gradual Migration (Recommended)

Keep both services running, gradually switch controllers:

```java
// In controllers, inject both services
@Autowired(required = false)
private FirebaseStorageService firebaseStorage;

@Autowired(required = false)
private AlibabaOssStorageService ossStorage;

// Use OSS if available, fallback to Firebase
StorageService storage = (ossStorage != null) ? ossStorage : firebaseStorage;
```

### Option B: Complete Switch

Replace all `FirebaseStorageService` injections with `AlibabaOssStorageService`.

## Step 4: Benefits After Migration

✅ **No more temporary public access needed**
- OSS signed URLs work perfectly with Alibaba AI services
- All services in same cloud (cn-shanghai)

✅ **Better performance**
- Lower latency (same region)
- No cross-cloud network issues

✅ **Lower costs**
- No cross-cloud data transfer fees
- Alibaba Cloud is cheaper in China

✅ **Simpler security**
- No need to make files public temporarily
- Signed URLs work natively with all Alibaba services

## Step 5: Data Migration (Optional)

If you want to migrate existing Firebase videos to OSS:

```bash
# Use gsutil and ossutil to transfer
gsutil -m cp -r gs://your-firebase-bucket/* /tmp/migration/
ossutil cp -r /tmp/migration/ oss://xpectra/videos/
```

Or create a migration script in Java (can provide if needed).

## Step 6: Update AlibabaVideoShotDetectionService

The service will automatically use OSS URLs without needing temporary public access:

```java
// Before (Firebase): Had to make file public temporarily
firebaseStorageService.makeFilePublic(videoUrl);

// After (OSS): Signed URLs work directly!
String signedUrl = ossStorage.generateSignedUrl(videoUrl, 7, TimeUnit.DAYS);
// Alibaba services can access this URL directly
```

## Folder Structure in OSS

```
xpectra/
├── videos/
│   └── {userId}/
│       └── {videoId}/
│           ├── original.mp4
│           └── thumbnail.jpg
├── keyframes/
│   └── {userId}/
│       └── {videoId}/
│           ├── scene-1-keyframe.jpg
│           ├── scene-2-keyframe.jpg
│           └── scene-3-keyframe.jpg
└── compiled/
    └── {userId}/
        └── {compilationId}/
            └── final-video.mp4
```

## Testing

1. Start application with OSS enabled
2. Upload a test video
3. Check OSS console: https://oss.console.aliyun.com/bucket/oss-cn-shanghai/xpectra
4. Verify video shot detection works without making files public

## Rollback Plan

If issues occur, simply switch back:

```properties
alibaba.oss.enabled=false
firebase.enabled=true
```

Application will use Firebase Storage again.
