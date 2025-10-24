# Firebase/Google Storage → Alibaba OSS Migration Plan

## 📊 Current Firebase/Google Storage Usage Analysis

### Files Using Firebase Storage:

1. **FirebaseStorageService.java** ⭐ Core service
   - Upload videos with thumbnails
   - Generate signed URLs
   - Delete files
   - Make files public/private

2. **AlibabaVideoShotDetectionService.java** 
   - Uses FirebaseStorageService to make files temporarily public
   - **Action**: Replace with OSS signed URLs (no public access needed!)

3. **VideoController.java** ⭐ Main upload endpoint
   - Uploads videos via FirebaseStorageService
   - Generates signed URLs for playback
   - **Action**: Switch to AlibabaOssStorageService

4. **ContentManager.java**
   - Generates signed URLs for video preview
   - **Action**: Switch to OSS signed URLs

5. **SceneReviewController.java**
   - Generates signed URLs for scene video playback
   - **Action**: Switch to OSS signed URLs

6. **ImageProxyController.java**
   - Proxies images and refreshes signed URLs
   - **Action**: Switch to OSS signed URLs

7. **VideoCompilationServiceImpl.java** ⭐ Video compilation
   - Uploads compiled videos
   - Uses GcsFileResolver for GCS operations
   - **Action**: Switch to OSS, update GcsFileResolver

8. **TemplateCascadeDeletionService.java**
   - Deletes videos from storage
   - **Action**: Switch to OSS deletion

9. **GcsFileResolver.java** ⭐ GCS file downloader
   - Downloads files from GCS for processing
   - **Action**: Create OssFileResolver

10. **CiTestConfig.java** (Test file)
    - Mocks FirebaseStorageService
    - **Action**: Create OSS mock

### Firebase Config Files:
- **FirebaseConfig.java** - Firebase initialization
- **FirestoreConfig.java** - Firestore database config
- **FirebaseCredentialsUtil.java** - Credentials helper

---

## 🎯 Migration Strategy: Phased Approach

### Phase 1: Parallel Services (RECOMMENDED - Low Risk)
Run both Firebase and OSS side-by-side, gradually migrate endpoints.

**Benefits:**
- ✅ Zero downtime
- ✅ Easy rollback
- ✅ Test OSS with real traffic
- ✅ Migrate users gradually

**Steps:**
1. Keep Firebase enabled
2. Enable OSS alongside
3. Create abstraction layer
4. Migrate endpoints one by one
5. Monitor and validate
6. Disable Firebase when ready

### Phase 2: Complete Switch (High Risk)
Replace all Firebase with OSS at once.

**Benefits:**
- ✅ Clean codebase
- ✅ No dual maintenance

**Risks:**
- ❌ All-or-nothing migration
- ❌ Hard to rollback
- ❌ Requires extensive testing

---

## 📋 Detailed Migration Plan

### Step 1: Create Abstraction Layer (Storage Interface)

Create a common interface so controllers don't care about Firebase vs OSS:

```java
public interface StorageService {
    UploadResult uploadVideoWithThumbnail(MultipartFile file, String userId, String videoId);
    String generateSignedUrl(String url);
    String generateSignedUrl(String url, long duration, TimeUnit unit);
    boolean deleteObjectByUrl(String url);
    int deleteByPrefix(String prefix);
    String uploadFile(File file, String objectKey, String contentType);
}
```

### Step 2: Implement Adapters

```java
@Service
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseStorageAdapter implements StorageService {
    @Autowired private FirebaseStorageService firebaseStorage;
    // Delegate all methods to firebaseStorage
}

@Service
@ConditionalOnProperty(name = "alibaba.oss.enabled", havingValue = "true")
public class AlibabaOssStorageAdapter implements StorageService {
    @Autowired private AlibabaOssStorageService ossStorage;
    // Delegate all methods to ossStorage
}
```

### Step 3: Create Storage Service Factory

```java
@Component
public class StorageServiceFactory {
    @Autowired(required = false) private FirebaseStorageAdapter firebaseAdapter;
    @Autowired(required = false) private AlibabaOssStorageAdapter ossAdapter;
    
    @Value("${storage.provider:firebase}")
    private String provider;
    
    public StorageService getStorageService() {
        if ("oss".equals(provider) && ossAdapter != null) {
            return ossAdapter;
        }
        if ("firebase".equals(provider) && firebaseAdapter != null) {
            return firebaseAdapter;
        }
        throw new IllegalStateException("No storage service available");
    }
}
```

### Step 4: Update Controllers

**Before:**
```java
@Autowired(required = false)
private FirebaseStorageService firebaseStorageService;

// Usage
firebaseStorageService.uploadVideoWithThumbnail(file, userId, videoId);
```

**After:**
```java
@Autowired
private StorageServiceFactory storageFactory;

// Usage
StorageService storage = storageFactory.getStorageService();
storage.uploadVideoWithThumbnail(file, userId, videoId);
```

### Step 5: Create OssFileResolver

Replace GcsFileResolver with OssFileResolver for video compilation:

```java
@Component
public class OssFileResolver {
    @Autowired private AlibabaOssStorageService ossStorage;
    
    public ResolvedFile resolve(String filePathOrUrl) throws IOException {
        if (isOssUrl(filePathOrUrl)) {
            return downloadFromOss(filePathOrUrl);
        }
        // Handle local files
    }
}
```

### Step 6: Update AlibabaVideoShotDetectionService

**Before (Firebase):**
```java
// Make file temporarily public
firebaseStorageService.makeFilePublic(videoUrl);
// Process...
firebaseStorageService.makeFilePrivate(videoUrl);
```

**After (OSS):**
```java
// Generate signed URL (works directly with Alibaba services!)
String signedUrl = ossStorage.generateSignedUrl(videoUrl, 7, TimeUnit.DAYS);
// Process with signedUrl - no public access needed!
```

---

## 🔧 Implementation Files to Create

### 1. StorageService.java (Interface)
```java
package com.example.demo.service;

public interface StorageService {
    // Common interface for both Firebase and OSS
}
```

### 2. FirebaseStorageAdapter.java
```java
package com.example.demo.service.adapter;

@Service
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseStorageAdapter implements StorageService {
    // Wraps FirebaseStorageService
}
```

### 3. AlibabaOssStorageAdapter.java
```java
package com.example.demo.service.adapter;

@Service
@ConditionalOnProperty(name = "alibaba.oss.enabled", havingValue = "true")
public class AlibabaOssStorageAdapter implements StorageService {
    // Wraps AlibabaOssStorageService
}
```

### 4. StorageServiceFactory.java
```java
package com.example.demo.service;

@Component
public class StorageServiceFactory {
    // Returns appropriate storage service based on config
}
```

### 5. OssFileResolver.java
```java
package com.example.demo.ai.shared;

@Component
public class OssFileResolver {
    // OSS version of GcsFileResolver
}
```

---

## 📝 Configuration Changes

### application.properties

```properties
# Storage Provider Selection
storage.provider=oss  # or "firebase"

# Firebase (keep for gradual migration)
firebase.enabled=false
firebase.storage.bucket=your-firebase-bucket

# Alibaba OSS (new)
alibaba.oss.enabled=true
alibaba.oss.bucket=xpectra
alibaba.oss.endpoint=oss-cn-shanghai.aliyuncs.com

# Alibaba Cloud Credentials
ALIBABA_CLOUD_ACCESS_KEY_ID=your_key
ALIBABA_CLOUD_ACCESS_KEY_SECRET=your_secret
```

---

## 🧪 Testing Plan

### 1. Unit Tests
- Test OSS upload/download
- Test signed URL generation
- Test file deletion

### 2. Integration Tests
- Upload video via VideoController
- Generate AI template (video shot detection)
- Compile videos
- Delete videos

### 3. Load Tests
- Upload 100 videos
- Generate 100 signed URLs
- Measure latency vs Firebase

### 4. Rollback Test
- Switch back to Firebase
- Verify everything still works

---

## 📊 Migration Checklist

### Pre-Migration
- [ ] Create OSS bucket (xpectra) ✅ DONE
- [ ] Configure OSS credentials
- [ ] Add OSS SDK to pom.xml ✅ DONE
- [ ] Create AlibabaOssStorageService ✅ DONE
- [ ] Create abstraction layer (StorageService interface)
- [ ] Create adapters (Firebase + OSS)
- [ ] Create StorageServiceFactory
- [ ] Create OssFileResolver
- [ ] Update all controllers to use factory
- [ ] Write tests

### Migration
- [ ] Enable OSS in config (storage.provider=oss)
- [ ] Test video upload
- [ ] Test AI template generation
- [ ] Test video compilation
- [ ] Test video deletion
- [ ] Monitor logs for errors
- [ ] Check OSS console for uploaded files

### Post-Migration
- [ ] Monitor performance (latency, errors)
- [ ] Verify all features work
- [ ] Migrate existing Firebase files to OSS (optional)
- [ ] Disable Firebase (firebase.enabled=false)
- [ ] Remove Firebase dependencies (optional)
- [ ] Update documentation

---

## 🚀 Quick Start (Minimal Changes)

If you want to start quickly without abstraction layer:

1. **Enable OSS**:
```properties
alibaba.oss.enabled=true
firebase.enabled=false
```

2. **Replace in VideoController.java**:
```java
@Autowired(required = false)
private AlibabaOssStorageService ossStorageService;

// Change all firebaseStorageService → ossStorageService
```

3. **Update AlibabaVideoShotDetectionService**:
```java
@Autowired(required = false)
private AlibabaOssStorageService ossStorageService;

// Use signed URLs instead of public access
```

4. **Test and iterate**

---

## 💰 Cost Comparison

### Firebase Storage (Google Cloud)
- Storage: $0.026/GB/month
- Download: $0.12/GB (first 1GB free)
- Operations: $0.05 per 10,000

### Alibaba OSS (cn-shanghai)
- Storage: ¥0.12/GB/month (~$0.017/GB)
- Download: ¥0.50/GB (~$0.07/GB)
- Operations: ¥0.01 per 10,000

**Savings: ~35% cheaper with OSS!**

---

## 🎯 Recommendation

**Use Phase 1 (Parallel Services) with Abstraction Layer**

This gives you:
- ✅ Safe migration path
- ✅ Easy rollback
- ✅ Test in production
- ✅ Clean architecture
- ✅ Future-proof (easy to add more storage providers)

Want me to implement the abstraction layer and adapters?
