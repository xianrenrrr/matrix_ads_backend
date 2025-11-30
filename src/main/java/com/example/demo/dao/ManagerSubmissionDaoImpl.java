package com.example.demo.dao;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * ManagerSubmissionDao - stores submissions under manager document
 * Structure: managerSubmissions/{managerId}/submissions/{submissionId}
 */
@Repository
public class ManagerSubmissionDaoImpl implements ManagerSubmissionDao {

    @Autowired
    private Firestore db;

    private static final String COLLECTION = "managerSubmissions";
    private static final String SUBCOLLECTION = "submissions";

    @Override
    public void saveSubmission(String managerId, Map<String, Object> submission) {
        try {
            String submissionId = (String) submission.get("id");
            if (submissionId == null) {
                throw new IllegalArgumentException("Submission must have an 'id' field");
            }

            db.collection(COLLECTION)
                    .document(managerId)
                    .collection(SUBCOLLECTION)
                    .document(submissionId)
                    .set(submission)
                    .get();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to save manager submission", e);
        }
    }

    @Override
    public List<Map<String, Object>> getSubmissions(String managerId) {
        try {
            List<Map<String, Object>> submissions = new ArrayList<>();

            List<QueryDocumentSnapshot> documents = db.collection(COLLECTION)
                    .document(managerId)
                    .collection(SUBCOLLECTION)
                    .get()
                    .get()
                    .getDocuments();

            for (QueryDocumentSnapshot doc : documents) {
                Map<String, Object> data = doc.getData();
                data.put("id", doc.getId());
                submissions.add(data);
            }

            return submissions;

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get manager submissions", e);
        }
    }

    @Override
    public void updateSubmissionStatus(String managerId, String submissionId, String status) {
        try {
            db.collection(COLLECTION)
                    .document(managerId)
                    .collection(SUBCOLLECTION)
                    .document(submissionId)
                    .update("publishStatus", status, "updatedAt", com.google.cloud.Timestamp.now())
                    .get();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to update submission status", e);
        }
    }

    @Override
    public void deleteSubmission(String managerId, String submissionId) {
        try {
            db.collection(COLLECTION)
                    .document(managerId)
                    .collection(SUBCOLLECTION)
                    .document(submissionId)
                    .delete()
                    .get();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to delete submission", e);
        }
    }

    @Override
    public void deleteByAssignmentId(String managerId, String assignmentId) {
        try {
            System.out.println("[MANAGER-SUBMISSION] Deleting submissions for manager: " + managerId + ", assignment: " + assignmentId);
            
            List<QueryDocumentSnapshot> documents = db.collection(COLLECTION)
                    .document(managerId)
                    .collection(SUBCOLLECTION)
                    .whereEqualTo("assignmentId", assignmentId)
                    .get()
                    .get()
                    .getDocuments();

            System.out.println("[MANAGER-SUBMISSION] Found " + documents.size() + " submissions to delete");
            
            for (QueryDocumentSnapshot doc : documents) {
                doc.getReference().delete().get();
                System.out.println("[MANAGER-SUBMISSION] Deleted submission: " + doc.getId());
            }

        } catch (InterruptedException | ExecutionException e) {
            System.err.println("[MANAGER-SUBMISSION] Error deleting submissions: " + e.getMessage());
            // Don't throw - just log the error and continue
            // This prevents cascade deletion from failing if managerSubmissions has issues
        }
    }

    @Override
    public Map<String, Object> getSubmission(String managerId, String submissionId) {
        try {
            var doc = db.collection(COLLECTION)
                    .document(managerId)
                    .collection(SUBCOLLECTION)
                    .document(submissionId)
                    .get()
                    .get();

            if (!doc.exists()) {
                return null;
            }

            Map<String, Object> data = doc.getData();
            data.put("id", doc.getId());
            return data;

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get submission", e);
        }
    }
}
