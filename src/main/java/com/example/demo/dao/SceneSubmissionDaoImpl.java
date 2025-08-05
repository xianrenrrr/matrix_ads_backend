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
    public void updateMultipleStatuses(List<String> sceneIds, String newStatus, String reviewerId) throws ExecutionException, InterruptedException {
        // Use batch write for better performance
        WriteBatch batch = db.batch();
        
        for (String sceneId : sceneIds) {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(sceneId);
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", newStatus);
            updates.put("reviewedBy", reviewerId);
            updates.put("reviewedAt", new Date());
            updates.put("lastUpdatedAt", new Date());
            
            batch.update(docRef, updates);
        }
        
        batch.commit().get();
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
}