package com.example.demo.dao;

import com.example.demo.model.SceneSubmission;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Firestore implementation of SceneSubmissionDao
 * Handles all database operations for scene-level submissions
 */
@Repository
public class SceneSubmissionDaoImpl implements SceneSubmissionDao {
    
    private static final String COLLECTION_NAME = "sceneSubmissions";
    
    @Autowired
    private Firestore db;
    
    @Autowired(required = false)
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
    @Autowired(required = false)
    private com.example.demo.service.VideoTranscodingService videoTranscodingService;
    
    @Override
    public String save(SceneSubmission sceneSubmission) throws ExecutionException, InterruptedException {
        CollectionReference collection = db.collection(COLLECTION_NAME);
        
        if (sceneSubmission.getId() == null) {
            // Create new document with auto-generated ID
            DocumentReference docRef = collection.document();
            sceneSubmission.setId(docRef.getId());
            docRef.set(sceneSubmission);
            return sceneSubmission.getId();
        } else {
            // Update existing document
            collection.document(sceneSubmission.getId()).set(sceneSubmission);
            return sceneSubmission.getId();
        }
    }
    
    @Override
    public SceneSubmission findById(String id) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(id);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        
        if (document.exists()) {
            return document.toObject(SceneSubmission.class);
        }
        return null;
    }
    
    @Override
    public void update(SceneSubmission sceneSubmission) throws ExecutionException, InterruptedException {
        if (sceneSubmission.getId() != null) {
            sceneSubmission.setLastUpdatedAt(new Date());
            db.collection(COLLECTION_NAME).document(sceneSubmission.getId()).set(sceneSubmission);
        }
    }
    
    @Override
    public void delete(String id) throws ExecutionException, InterruptedException {
        db.collection(COLLECTION_NAME).document(id).delete();
    }
    
    @Override
    public List<SceneSubmission> findByTemplateId(String templateId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("templateId", templateId);
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findByUserId(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("userId", userId);
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .whereEqualTo("userId", userId)
                        .orderBy("sceneNumber");
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findByStatus(String status) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("status", status)
                        .orderBy("submittedAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findByTemplateIdAndStatus(String templateId, String status) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .whereEqualTo("status", status)
                        .orderBy("sceneNumber");
        return executeQuery(query);
    }
    
    @Override
    public SceneSubmission findByTemplateIdAndUserIdAndSceneNumber(String templateId, String userId, int sceneNumber) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("sceneNumber", sceneNumber)
                        .orderBy("submittedAt", Query.Direction.DESCENDING)
                        .limit(1);
        
        List<SceneSubmission> results = executeQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public List<SceneSubmission> findApprovedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("status", "approved")
                        .orderBy("sceneNumber");
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findPendingScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("status", "pending")
                        .orderBy("sceneNumber");
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findRejectedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("status", "rejected")
                        .orderBy("sceneNumber");
        return executeQuery(query);
    }
    
    @Override
    public int countScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        List<SceneSubmission> scenes = findByTemplateIdAndUserId(templateId, userId);
        return scenes.size();
    }
    
    @Override
    public int countApprovedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        List<SceneSubmission> scenes = findApprovedScenesByTemplateIdAndUserId(templateId, userId);
        return scenes.size();
    }
    
    @Override
    public int countPendingScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        List<SceneSubmission> scenes = findPendingScenesByTemplateIdAndUserId(templateId, userId);
        return scenes.size();
    }
    
    @Override
    public int countRejectedScenesByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        List<SceneSubmission> scenes = findRejectedScenesByTemplateIdAndUserId(templateId, userId);
        return scenes.size();
    }
    
    @Override
    public List<SceneSubmission> findPendingSubmissionsForReview() throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("status", "pending")
                        .orderBy("submittedAt");
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findSubmissionsByReviewer(String reviewerId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("reviewedBy", reviewerId)
                        .orderBy("reviewedAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findRecentSubmissions(int limit) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .orderBy("submittedAt", Query.Direction.DESCENDING)
                        .limit(limit);
        return executeQuery(query);
    }
    
    @Override
    public List<SceneSubmission> findResubmissionHistory(String originalSceneId) throws ExecutionException, InterruptedException {
        // Find all submissions that reference this as previous submission
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("previousSubmissionId", originalSceneId)
                        .orderBy("submittedAt");
        return executeQuery(query);
    }
    
    @Override
    public SceneSubmission findLatestSubmissionForScene(String templateId, String userId, int sceneNumber) throws ExecutionException, InterruptedException {
        return findByTemplateIdAndUserIdAndSceneNumber(templateId, userId, sceneNumber);
    }
    
    @Override
    public boolean areAllScenesApproved(String templateId, String userId, int totalScenes) throws ExecutionException, InterruptedException {
        int approvedCount = countApprovedScenesByTemplateIdAndUserId(templateId, userId);
        return approvedCount == totalScenes;
    }
    
    @Override
    public List<SceneSubmission> getApprovedScenesInOrder(String templateId, String userId) throws ExecutionException, InterruptedException {
        return findApprovedScenesByTemplateIdAndUserId(templateId, userId);
    }
    
    @Override
    public List<SceneSubmission> findSubmissionsByDateRange(Date startDate, Date endDate) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereGreaterThanOrEqualTo("submittedAt", startDate)
                        .whereLessThanOrEqualTo("submittedAt", endDate)
                        .orderBy("submittedAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public double getAverageSimilarityScore(String templateId) throws ExecutionException, InterruptedException {
        List<SceneSubmission> submissions = findByTemplateId(templateId);
        
        double totalScore = 0;
        int count = 0;
        
        for (SceneSubmission submission : submissions) {
            if (submission.getSimilarityScore() != null) {
                totalScore += submission.getSimilarityScore();
                count++;
            }
        }
        
        return count > 0 ? totalScore / count : 0.0;
    }
    
    @Override
    public List<SceneSubmission> findTopPerformingScenes(String templateId, int limit) throws ExecutionException, InterruptedException {
        List<SceneSubmission> submissions = findByTemplateId(templateId);
        
        return submissions.stream()
                .filter(s -> s.getSimilarityScore() != null)
                .sorted((s1, s2) -> Double.compare(s2.getSimilarityScore(), s1.getSimilarityScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteScenesByTemplateId(String templateId) throws ExecutionException, InterruptedException {
        List<SceneSubmission> scenes = findByTemplateId(templateId);
        WriteBatch batch = db.batch();
        
        for (SceneSubmission scene : scenes) {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(scene.getId());
            batch.delete(docRef);
        }
        
        batch.commit().get();
    }
    
    @Override
    public void deleteScenesByUserId(String userId) throws ExecutionException, InterruptedException {
        List<SceneSubmission> scenes = findByUserId(userId);
        WriteBatch batch = db.batch();
        
        for (SceneSubmission scene : scenes) {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(scene.getId());
            batch.delete(docRef);
        }
        
        batch.commit().get();
    }
    
    // Helper method to execute queries and convert results
    private List<SceneSubmission> executeQuery(Query query) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
        
        List<SceneSubmission> results = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            SceneSubmission submission = document.toObject(SceneSubmission.class);
            submission.setId(document.getId());
            results.add(submission);
        }
        
        return results;
    }
    
    @Override
    public SceneSubmission uploadAndSaveScene(org.springframework.web.multipart.MultipartFile file, String assignmentId, String userId, int sceneNumber, String sceneTitle) throws Exception {
        if (ossStorageService == null) {
            throw new IllegalStateException("AlibabaOssStorageService not available");
        }
        
        // Check if video needs transcoding for WeChat compatibility
        java.io.File tempInputFile = null;
        java.io.File transcodedFile = null;
        org.springframework.web.multipart.MultipartFile fileToUpload = file;
        
        if (videoTranscodingService != null) {
            try {
                // Save to temp file for codec check
                tempInputFile = java.io.File.createTempFile("scene_check_", ".mp4");
                file.transferTo(tempInputFile);
                
                if (videoTranscodingService.needsTranscoding(tempInputFile)) {
                    System.out.println("[SCENE-UPLOAD] Video needs transcoding for WeChat compatibility");
                    transcodedFile = videoTranscodingService.transcodeIfNeeded(tempInputFile);
                    
                    if (transcodedFile != tempInputFile) {
                        // Transcoding succeeded, use transcoded file
                        System.out.println("[SCENE-UPLOAD] Using transcoded video: " + transcodedFile.length() + " bytes");
                        final java.io.File finalTranscodedFile = transcodedFile;
                        fileToUpload = new org.springframework.web.multipart.MultipartFile() {
                            @Override public String getName() { return "file"; }
                            @Override public String getOriginalFilename() { return file.getOriginalFilename(); }
                            @Override public String getContentType() { return "video/mp4"; }
                            @Override public boolean isEmpty() { return finalTranscodedFile.length() == 0; }
                            @Override public long getSize() { return finalTranscodedFile.length(); }
                            @Override public byte[] getBytes() throws java.io.IOException {
                                return java.nio.file.Files.readAllBytes(finalTranscodedFile.toPath());
                            }
                            @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                                return new java.io.FileInputStream(finalTranscodedFile);
                            }
                            @Override public void transferTo(java.io.File dest) throws java.io.IOException {
                                java.nio.file.Files.copy(finalTranscodedFile.toPath(), dest.toPath(), 
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        };
                    }
                } else {
                    System.out.println("[SCENE-UPLOAD] Video codec is WeChat compatible, no transcoding needed");
                }
            } catch (Exception e) {
                System.err.println("[SCENE-UPLOAD] Transcoding check failed, using original: " + e.getMessage());
            }
        }
        
        // Upload to OSS
        String sceneVideoId = UUID.randomUUID().toString();
        com.example.demo.service.AlibabaOssStorageService.UploadResult uploadResult = 
            ossStorageService.uploadVideoWithThumbnail(fileToUpload, userId, sceneVideoId);
        
        // Clean up temp files
        if (tempInputFile != null && tempInputFile.exists()) {
            tempInputFile.delete();
        }
        if (transcodedFile != null && transcodedFile != tempInputFile && transcodedFile.exists()) {
            transcodedFile.delete();
        }
        
        // Create scene submission
        SceneSubmission sceneSubmission = new SceneSubmission(assignmentId, userId, sceneNumber, sceneTitle);
        sceneSubmission.setVideoUrl(uploadResult.videoUrl);
        sceneSubmission.setThumbnailUrl(uploadResult.thumbnailUrl);
        sceneSubmission.setOriginalFileName(file.getOriginalFilename());
        sceneSubmission.setFileSize(file.getSize());
        sceneSubmission.setFormat(getFileExtension(file.getOriginalFilename()));
        sceneSubmission.setSimilarityScore(-1.0);
        sceneSubmission.setAiSuggestions(Arrays.asList("AI分析进行中...", "请稍后查看结果"));
        sceneSubmission.setStatus("pending");
        
        // Save to database
        String sceneId = save(sceneSubmission);
        sceneSubmission.setId(sceneId);
        
        return sceneSubmission;
    }
    
    @Override
    public String getSignedUrl(String videoUrl) throws Exception {
        if (ossStorageService == null || videoUrl == null) {
            return videoUrl;
        }
        return ossStorageService.generateSignedUrl(videoUrl);
    }
    
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return "mp4";
    }
}