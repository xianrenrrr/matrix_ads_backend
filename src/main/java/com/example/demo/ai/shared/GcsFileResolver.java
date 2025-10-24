package com.example.demo.ai.shared;

import com.example.demo.util.FirebaseCredentialsUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to resolve GCS URLs to local files.
 * If input is a GCS URL, downloads to temp file.
 * If input is already a local path, returns it unchanged.
 */
@Component
public class GcsFileResolver {
    
    private static final Logger log = LoggerFactory.getLogger(GcsFileResolver.class);
    
    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;
    
    @Value("${alibaba.oss.bucket-name}")
    private String bucketName;
    
    private Storage storageClient;
    
    /**
     * Resolve a file path or GCS URL to a local file path.
     * 
     * @param filePathOrUrl Either a local file path or a GCS URL
     * @return Local file path (may be temporary if downloaded from GCS)
     * @throws IOException if download fails
     */
    public ResolvedFile resolve(String filePathOrUrl) throws IOException {
        if (filePathOrUrl == null || filePathOrUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("File path or URL cannot be null or empty");
        }
        
        // Check if it's a GCS URL
        if (isGcsUrl(filePathOrUrl)) {
            return downloadFromGcs(filePathOrUrl);
        }
        
        // It's already a local path
        Path localPath = Paths.get(filePathOrUrl);
        if (!Files.exists(localPath)) {
            throw new IOException("Local file does not exist: " + filePathOrUrl);
        }
        
        log.debug("Using local file: {}", filePathOrUrl);
        return new ResolvedFile(localPath, false);
    }
    
    /**
     * Check if a string is a Google Cloud Storage URL
     */
    private boolean isGcsUrl(String url) {
        return url.startsWith("https://storage.googleapis.com/") || 
               url.startsWith("gs://");
    }
    
    /**
     * Download a file from Google Cloud Storage to a temporary local file
     */
    private ResolvedFile downloadFromGcs(String gcsUrl) throws IOException {
        log.info("Downloading from GCS: {}", gcsUrl);
        
        // Extract object name from URL
        String objectName = extractObjectName(gcsUrl);
        
        // Get or create storage client
        Storage storage = getStorageClient();
        
        // Get the blob
        Blob blob = storage.get(bucketName, objectName);
        if (blob == null || !blob.exists()) {
            throw new IOException("Object not found in GCS: " + objectName);
        }
        
        // Create temporary file
        String extension = getFileExtension(objectName);
        Path tempFile = Files.createTempFile("gcs_download_", extension);
        
        try {
            // Download to temp file
            blob.downloadTo(tempFile);
            log.info("Downloaded {} bytes to temp file: {}", blob.getSize(), tempFile);
            
            return new ResolvedFile(tempFile, true);
            
        } catch (Exception e) {
            // Clean up temp file if download fails
            Files.deleteIfExists(tempFile);
            throw new IOException("Failed to download from GCS: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract object name from GCS URL
     */
    private String extractObjectName(String gcsUrl) {
        if (gcsUrl.startsWith("gs://")) {
            // Format: gs://bucket-name/object-path
            String withoutPrefix = gcsUrl.substring(5); // Remove "gs://"
            int slashIndex = withoutPrefix.indexOf('/');
            if (slashIndex > 0) {
                return withoutPrefix.substring(slashIndex + 1);
            }
            throw new IllegalArgumentException("Invalid GCS URL format: " + gcsUrl);
        } else if (gcsUrl.startsWith("https://storage.googleapis.com/")) {
            // Format: https://storage.googleapis.com/bucket-name/object-path[?query]
            String path = gcsUrl.replace("https://storage.googleapis.com/", "");
            // Strip query parameters if present
            int qIdx = path.indexOf('?');
            if (qIdx >= 0) {
                path = path.substring(0, qIdx);
            }
            if (path.startsWith(bucketName + "/")) {
                return path.substring(bucketName.length() + 1);
            }
            // Try to extract from any bucket
            int slashIndex = path.indexOf('/');
            if (slashIndex > 0) {
                return path.substring(slashIndex + 1);
            }
        }
        throw new IllegalArgumentException("Cannot extract object name from URL: " + gcsUrl);
    }
    
    /**
     * Get file extension including the dot
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(lastDot);
        }
        return ".tmp";
    }
    
    /**
     * Get or create the Storage client
     */
    private synchronized Storage getStorageClient() throws IOException {
        if (storageClient == null) {
            GoogleCredentials credentials = firebaseCredentialsUtil.getCredentials();
            storageClient = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
        }
        return storageClient;
    }
    
    /**
     * Result of file resolution
     */
    public static class ResolvedFile implements AutoCloseable {
        private final Path path;
        private final boolean isTemporary;
        
        public ResolvedFile(Path path, boolean isTemporary) {
            this.path = path;
            this.isTemporary = isTemporary;
        }
        
        public Path getPath() {
            return path;
        }
        
        public String getPathAsString() {
            return path.toString();
        }
        
        public boolean isTemporary() {
            return isTemporary;
        }
        
        @Override
        public void close() {
            if (isTemporary) {
                try {
                    Files.deleteIfExists(path);
                    log.debug("Deleted temporary file: {}", path);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file: {}", path, e);
                }
            }
        }
    }
}
