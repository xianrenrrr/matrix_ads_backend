package com.example.demo.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.firebase.FirebaseApp;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.WritableByteChannel;
import com.google.firebase.cloud.StorageClient;

@Service
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseStorageService {
    private final Storage storage;
    private final String bucketName;
    private final FirebaseApp firebaseApp;

    @Autowired
    public FirebaseStorageService(@Value("${firebase.storage.bucket}") String bucketName, FirebaseApp firebaseApp) {
        this.firebaseApp = firebaseApp;
        this.bucketName = bucketName;
        
        if (firebaseApp == null) {
            throw new IllegalStateException("Firebase is not properly initialized. Please check your Firebase configuration.");
        }
        
        this.storage = StorageClient.getInstance(firebaseApp).bucket(bucketName).getStorage();
    }

    public static class UploadResult {
        public final String videoUrl;
        public final String thumbnailUrl;
        public UploadResult(String videoUrl, String thumbnailUrl) {
            this.videoUrl = videoUrl;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public UploadResult uploadVideoWithThumbnail(MultipartFile file, String userId, String videoId) throws IOException, InterruptedException {
    // Stream upload video to Firebase Storage directly from MultipartFile InputStream
        String objectName = String.format("videos/%s/%s/%s", userId, videoId, file.getOriginalFilename());
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
        .setContentType(file.getContentType())
            .build();
        try (InputStream is = file.getInputStream();
            WritableByteChannel writer = storage.writer(blobInfo)) {
            byte[] buffer = new byte[1024];
            int limit;
            while ((limit = is.read(buffer)) >= 0) {
                writer.write(java.nio.ByteBuffer.wrap(buffer, 0, limit));
            }
        }
        
        String videoUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, objectName);
        // Save video to temp file for FFmpeg thumbnail extraction
        java.io.File tempVideo = java.io.File.createTempFile("upload-", ".mp4");
        file.transferTo(tempVideo);
        // Extract thumbnail using FFmpeg
        String thumbName = String.format("videos/%s/%s/thumbnail.jpg", userId, videoId);
        java.io.File tempThumb = java.io.File.createTempFile("thumb-", ".jpg");
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y", "-ss", "1", "-i", tempVideo.getAbsolutePath(), "-frames:v", "1", tempThumb.getAbsolutePath()
        );
        Process proc = pb.start();
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            tempVideo.delete();
            tempThumb.delete();
            throw new IOException("Failed to extract thumbnail with FFmpeg");
        }
        // Upload thumbnail
        BlobInfo thumbBlob = BlobInfo.newBuilder(bucketName, thumbName)
                .setContentType("image/jpeg")
                .build();
        storage.create(thumbBlob, java.nio.file.Files.readAllBytes(tempThumb.toPath()));
        
        String thumbnailUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, thumbName);
        // Clean up
        tempVideo.delete();
        tempThumb.delete();
        // Log
        System.out.println("[2025-05-01] Uploaded video to: " + videoUrl);
        System.out.println("[2025-05-01] Uploaded thumbnail to: " + thumbnailUrl);
        return new UploadResult(videoUrl, thumbnailUrl);
    }

    public String generateSignedUrl(String firebaseUrl) {
        return generateSignedUrl(firebaseUrl, 15, TimeUnit.MINUTES);
    }
    
    /**
     * Generate signed URL with custom expiration time
     * Useful for AI services that need longer access (e.g., Alibaba Cloud video processing)
     */
    public String generateSignedUrl(String firebaseUrl, long duration, TimeUnit unit) {
        try {
            // Extract object name from Firebase Storage URL
            // URL format: https://storage.googleapis.com/bucket-name/path/to/object
            if (!firebaseUrl.contains("storage.googleapis.com")) {
                System.out.println("Not a Firebase Storage URL, returning as-is");
                return firebaseUrl; // Return as-is if not a Firebase Storage URL
            }
            
            // Remove query parameters if present (e.g., existing signed URL params)
            String cleanUrl = firebaseUrl.split("\\?")[0];
            
            // Extract object name
            String objectName = cleanUrl.substring(cleanUrl.indexOf(bucketName) + bucketName.length() + 1);
            
            System.out.println("Generating signed URL for object: " + objectName);
            System.out.println("Duration: " + duration + " " + unit);
            
            // Generate signed URL with specified expiration
            URL signedUrl = storage.signUrl(
                BlobInfo.newBuilder(bucketName, objectName).build(),
                duration, unit
            );
            
            String signedUrlString = signedUrl.toString();
            System.out.println("Successfully generated signed URL (length: " + signedUrlString.length() + " chars)");
            
            return signedUrlString;
        } catch (Exception e) {
            System.err.println("Error generating signed URL: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Falling back to original URL");
            return firebaseUrl;
        } // Fallback to original URL
    }

    /**
     * Parse an object path from a Firebase Storage URL of the form
     * https://storage.googleapis.com/{bucket}/{object}[?query]
     * Returns null if the URL does not match the expected host/bucket.
     */
    public String parseObjectNameFromUrl(String firebaseUrl) {
        try {
            if (firebaseUrl == null || !firebaseUrl.contains("storage.googleapis.com")) return null;
            java.net.URI uri = java.net.URI.create(firebaseUrl);
            String path = uri.getPath(); // /{bucket}/{object}
            if (path == null || path.isEmpty()) return null;
            if (path.startsWith("/")) path = path.substring(1);
            if (!path.startsWith(bucketName + "/")) return null;
            String objectName = path.substring(bucketName.length() + 1);
            return objectName.isEmpty() ? null : objectName;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compose multiple objects in the same bucket into a single object.
     * All source URLs must point to this bucket. Returns the HTTPS URL of the new object.
     */
    public String composeObjects(java.util.List<String> sourceUrls, String destinationObjectName, String contentType) throws IOException {
        if (sourceUrls == null || sourceUrls.isEmpty()) {
            throw new IllegalArgumentException("No source URLs provided for composition");
        }
        // Older GCS client expects a list of source object names (Iterable<String>)
        java.util.List<String> sourceNames = new java.util.ArrayList<>();
        for (String url : sourceUrls) {
            String objectName = parseObjectNameFromUrl(url);
            if (objectName == null) {
                throw new IllegalArgumentException("Source URL is not in expected bucket: " + url);
            }
            sourceNames.add(objectName);
        }
        BlobInfo target = BlobInfo.newBuilder(bucketName, destinationObjectName)
                .setContentType(contentType != null ? contentType : "application/octet-stream")
                .build();
        storage.compose(Storage.ComposeRequest.newBuilder()
                .setTarget(target)
                .addSource(sourceNames)
                .build());
        return String.format("https://storage.googleapis.com/%s/%s", bucketName, destinationObjectName);
    }

    /** Upload a local file to the given destination path and return its HTTPS URL. */
    public String uploadFile(java.io.File file, String destinationObjectName, String contentType) throws IOException {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, destinationObjectName)
                .setContentType(contentType != null ? contentType : "application/octet-stream")
                .build();
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        storage.create(blobInfo, bytes);
        return String.format("https://storage.googleapis.com/%s/%s", bucketName, destinationObjectName);
    }
    /**
     * Delete a single object by its Firebase Storage URL. Returns true if deleted or not found,
     * false only on unexpected errors.
     */
    public boolean deleteObjectByUrl(String firebaseUrl) {
        try {
            String objectName = parseObjectNameFromUrl(firebaseUrl);
            if (objectName == null) return true; // Nothing to do
            var blob = storage.get(bucketName, objectName);
            if (blob == null) return true; // Already gone
            return blob.delete();
        } catch (Exception e) {
            System.err.println("[DELETE] Failed to delete object by URL: " + redact(firebaseUrl) + " error=" + e);
            return false;
        }
    }

    /**
     * Delete all objects with the given prefix. Returns number of deleted objects.
     */
    public int deleteByPrefix(String prefix) {
        int deleted = 0;
        try {
            if (prefix == null) return 0;
            var blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix)).iterateAll();
            for (var blob : blobs) {
                try {
                    if (blob.delete()) deleted++;
                } catch (Exception e) {
                    System.err.println("[DELETE] Failed to delete object: " + blob.getName() + " error=" + e);
                }
            }
        } catch (Exception e) {
            System.err.println("[DELETE] List by prefix failed for " + prefix + " error=" + e);
        }
        return deleted;
    }

    private String redact(String url) {
        if (url == null) return null;
        return url.replaceAll("(Signature=)[^&]+", "$1<redacted>");
    }
    
    /**
     * Make a file temporarily public (for AI services that can't access signed URLs)
     * WARNING: This makes the file publicly accessible to anyone with the URL
     */
    public void makeFilePublic(String firebaseUrl) {
        try {
            String objectName = parseObjectNameFromUrl(firebaseUrl);
            if (objectName == null) {
                System.err.println("Cannot make file public - invalid URL: " + firebaseUrl);
                return;
            }
            
            var blob = storage.get(bucketName, objectName);
            if (blob == null) {
                System.err.println("Cannot make file public - file not found: " + objectName);
                return;
            }
            
            // Create ACL to make file publicly readable
            blob.createAcl(com.google.cloud.storage.Acl.of(
                com.google.cloud.storage.Acl.User.ofAllUsers(), 
                com.google.cloud.storage.Acl.Role.READER
            ));
            
            System.out.println("Made file public: " + objectName);
        } catch (Exception e) {
            System.err.println("Failed to make file public: " + e.getMessage());
            throw new RuntimeException("Failed to make file public", e);
        }
    }
    
    /**
     * Make a file private again (remove public access)
     */
    public void makeFilePrivate(String firebaseUrl) {
        try {
            String objectName = parseObjectNameFromUrl(firebaseUrl);
            if (objectName == null) {
                System.err.println("Cannot make file private - invalid URL: " + firebaseUrl);
                return;
            }
            
            var blob = storage.get(bucketName, objectName);
            if (blob == null) {
                System.err.println("Cannot make file private - file not found: " + objectName);
                return;
            }
            
            // Delete the public ACL
            try {
                blob.deleteAcl(com.google.cloud.storage.Acl.User.ofAllUsers());
                System.out.println("Made file private: " + objectName);
            } catch (Exception e) {
                // ACL might not exist, which is fine
                System.out.println("File was already private or ACL not found: " + objectName);
            }
        } catch (Exception e) {
            System.err.println("Failed to make file private: " + e.getMessage());
            throw new RuntimeException("Failed to make file private", e);
        }
    }
}
