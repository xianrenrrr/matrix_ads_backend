package com.example.demo.ai.template;

import com.example.demo.util.FirebaseCredentialsUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BlockGridServiceImpl implements BlockGridService {
    
    @Autowired
    private FirebaseCredentialsUtil firebaseCredentialsUtil;

    @Value("${firebase.storage.bucket}")
    private String bucketName;
    
    private static final String BLOCKS_FOLDER = "blocks/";

    @Override
    public Map<String, String> createBlockGrid(String imageUrl) {
        System.out.printf("Creating 3x3 block grid for image: %s%n", imageUrl);
        
        Map<String, String> blockImageUrls = new HashMap<>();
        
        try {
            // Get credentials using utility (environment or file)
            GoogleCredentials credentials = firebaseCredentialsUtil.getCredentials();
            
            // Extract object name from GCS URL
            String objectName = imageUrl.replace("https://storage.googleapis.com/" + bucketName + "/", "");
            
            // Create storage client with credentials
            Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
            Blob imageBlob = storage.get(bucketName, objectName);
            
            if (imageBlob == null || !imageBlob.exists()) {
                throw new IOException("Image not found in Cloud Storage: " + objectName);
            }
            
            byte[] imageBytes = imageBlob.getContent();
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            
            if (originalImage == null) {
                throw new IOException("Failed to read image: " + imageUrl);
            }
            
            int imageWidth = originalImage.getWidth();
            int imageHeight = originalImage.getHeight();
            
            // Calculate block dimensions (3x3 grid)
            int blockWidth = imageWidth / 3;
            int blockHeight = imageHeight / 3;
            
            // Create 9 blocks
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    // Calculate crop coordinates
                    int x = col * blockWidth;
                    int y = row * blockHeight;
                    int width = (col == 2) ? imageWidth - x : blockWidth; // Handle remainder for last column
                    int height = (row == 2) ? imageHeight - y : blockHeight; // Handle remainder for last row
                    
                    // Crop the image block
                    BufferedImage blockImage = originalImage.getSubimage(x, y, width, height);
                    
                    // Convert to byte array
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(blockImage, "jpg", baos);
                    byte[] blockBytes = baos.toByteArray();
                    
                    // Upload block to Cloud Storage
                    String blockKey = String.format("%d_%d", row, col);
                    String blockObjectName = BLOCKS_FOLDER + UUID.randomUUID().toString() + "_" + blockKey + ".jpg";
                    
                    BlobId blobId = BlobId.of(bucketName, blockObjectName);
                    BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType("image/jpeg")
                        .build();
                    
                    storage.create(blobInfo, blockBytes);
                    
                    String blockUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, blockObjectName);
                    blockImageUrls.put(blockKey, blockUrl);
                    
                    System.out.printf("Created block %s: %s%n", blockKey, blockUrl);
                }
            }
            
            System.out.printf("Successfully created %d blocks for image: %s%n", blockImageUrls.size(), imageUrl);
            return blockImageUrls;
            
        } catch (IOException e) {
            System.err.printf("Error creating block grid for image %s: %s%n", imageUrl, e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}