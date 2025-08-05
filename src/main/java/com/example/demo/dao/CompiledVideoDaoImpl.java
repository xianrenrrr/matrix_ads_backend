package com.example.demo.dao;

import com.example.demo.model.CompiledVideo;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Firestore implementation of CompiledVideoDao
 * Handles all database operations for compiled videos
 */
@Repository
public class CompiledVideoDaoImpl implements CompiledVideoDao {
    
    private static final String COLLECTION_NAME = "compiledVideos";
    
    @Autowired
    private Firestore db;
    
    @Override
    public String save(CompiledVideo compiledVideo) throws ExecutionException, InterruptedException {
        CollectionReference collection = db.collection(COLLECTION_NAME);
        
        if (compiledVideo.getId() == null) {
            // Create new document with auto-generated ID
            DocumentReference docRef = collection.document();
            compiledVideo.setId(docRef.getId());
            docRef.set(compiledVideo);
            return compiledVideo.getId();
        } else {
            // Update existing document
            collection.document(compiledVideo.getId()).set(compiledVideo);
            return compiledVideo.getId();
        }
    }
    
    @Override
    public CompiledVideo findById(String id) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(id);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        
        if (document.exists()) {
            CompiledVideo video = document.toObject(CompiledVideo.class);
            video.setId(document.getId());
            return video;
        }
        return null;
    }
    
    @Override
    public void update(CompiledVideo compiledVideo) throws ExecutionException, InterruptedException {
        if (compiledVideo.getId() != null) {
            db.collection(COLLECTION_NAME).document(compiledVideo.getId()).set(compiledVideo);
        }
    }
    
    @Override
    public void delete(String id) throws ExecutionException, InterruptedException {
        db.collection(COLLECTION_NAME).document(id).delete();
    }
    
    @Override
    public List<CompiledVideo> findByTemplateId(String templateId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .orderBy("compiledAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public List<CompiledVideo> findByUserId(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("userId", userId)
                        .orderBy("compiledAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public CompiledVideo findByTemplateIdAndUserId(String templateId, String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("templateId", templateId)
                        .whereEqualTo("userId", userId)
                        .orderBy("compiledAt", Query.Direction.DESCENDING)
                        .limit(1);
        
        List<CompiledVideo> results = executeQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public List<CompiledVideo> findByStatus(String status) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("status", status)
                        .orderBy("compiledAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public List<CompiledVideo> findCompiling() throws ExecutionException, InterruptedException {
        return findByStatus("compiling");
    }
    
    @Override
    public List<CompiledVideo> findCompleted() throws ExecutionException, InterruptedException {
        return findByStatus("completed");
    }
    
    @Override
    public List<CompiledVideo> findFailed() throws ExecutionException, InterruptedException {
        return findByStatus("failed");
    }
    
    @Override
    public List<CompiledVideo> findFailedRetryable() throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("status", "failed")
                        .whereLessThan("retryCount", 3)
                        .orderBy("retryCount")
                        .orderBy("compiledAt");
        return executeQuery(query);
    }
    
    @Override
    public CompiledVideo findByCompilationJobId(String jobId) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("compilationJobId", jobId)
                        .limit(1);
        
        List<CompiledVideo> results = executeQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public List<CompiledVideo> findByCompiledBy(String compiledBy) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("compiledBy", compiledBy)
                        .orderBy("compiledAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public List<CompiledVideo> findRecentCompilations(int limit) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .orderBy("compiledAt", Query.Direction.DESCENDING)
                        .limit(limit);
        return executeQuery(query);
    }
    
    @Override
    public List<CompiledVideo> findByDateRange(Date startDate, Date endDate) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereGreaterThanOrEqualTo("compiledAt", startDate)
                        .whereLessThanOrEqualTo("compiledAt", endDate)
                        .orderBy("compiledAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public double getAverageCompilationTime() throws ExecutionException, InterruptedException {
        List<CompiledVideo> completedVideos = findCompleted();
        
        long totalCompilationTime = 0;
        int count = 0;
        
        for (CompiledVideo video : completedVideos) {
            if (video.getCompiledAt() != null && video.getCompletedAt() != null) {
                long compilationTime = video.getCompletedAt().getTime() - video.getCompiledAt().getTime();
                totalCompilationTime += compilationTime;
                count++;
            }
        }
        
        if (count > 0) {
            // Return average in seconds
            return (double) totalCompilationTime / count / 1000.0;
        }
        
        return 0.0;
    }
    
    @Override
    public int countCompilationsByStatus(String status) throws ExecutionException, InterruptedException {
        List<CompiledVideo> videos = findByStatus(status);
        return videos.size();
    }
    
    @Override
    public List<CompiledVideo> findPublished() throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("publishStatus", "published")
                        .orderBy("publishedAt", Query.Direction.DESCENDING);
        return executeQuery(query);
    }
    
    @Override
    public List<CompiledVideo> findReadyForPublishing() throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("status", "completed")
                        .whereIn("publishStatus", Arrays.asList(null, "not_published"))
                        .orderBy("completedAt");
        return executeQuery(query);
    }
    
    @Override
    public void updatePublishStatus(String id, String publishStatus) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(id);
        Map<String, Object> updates = new HashMap<>();
        updates.put("publishStatus", publishStatus);
        
        if ("published".equals(publishStatus)) {
            updates.put("publishedAt", new Date());
        }
        
        docRef.update(updates).get();
    }
    
    @Override
    public void deleteByTemplateId(String templateId) throws ExecutionException, InterruptedException {
        List<CompiledVideo> videos = findByTemplateId(templateId);
        WriteBatch batch = db.batch();
        
        for (CompiledVideo video : videos) {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(video.getId());
            batch.delete(docRef);
        }
        
        batch.commit().get();
    }
    
    @Override
    public void deleteByUserId(String userId) throws ExecutionException, InterruptedException {
        List<CompiledVideo> videos = findByUserId(userId);
        WriteBatch batch = db.batch();
        
        for (CompiledVideo video : videos) {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(video.getId());
            batch.delete(docRef);
        }
        
        batch.commit().get();
    }
    
    @Override
    public void deleteOldFailedCompilations(Date cutoffDate) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                        .whereEqualTo("status", "failed")
                        .whereLessThan("compiledAt", cutoffDate);
        
        List<CompiledVideo> oldFailedVideos = executeQuery(query);
        WriteBatch batch = db.batch();
        
        for (CompiledVideo video : oldFailedVideos) {
            DocumentReference docRef = db.collection(COLLECTION_NAME).document(video.getId());
            batch.delete(docRef);
        }
        
        if (!oldFailedVideos.isEmpty()) {
            batch.commit().get();
        }
    }
    
    // Helper method to execute queries and convert results
    private List<CompiledVideo> executeQuery(Query query) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
        
        List<CompiledVideo> results = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            CompiledVideo video = document.toObject(CompiledVideo.class);
            video.setId(document.getId());
            results.add(video);
        }
        
        return results;
    }
}