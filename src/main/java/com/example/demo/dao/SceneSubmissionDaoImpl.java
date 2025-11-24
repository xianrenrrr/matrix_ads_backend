package com.example.demo.dao;

import com.example.demo.model.SceneSubmission;
import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class SceneSubmissionDaoImpl implements SceneSubmissionDao {
    
    private static final String TABLE_NAME = "sceneSubmissions";
    
    @Autowired
    private SyncClient tablestoreClient;
    
    @Autowired(required = false)
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String save(SceneSubmission sceneSubmission) {
        try {
            if (sceneSubmission.getId() == null) {
                sceneSubmission.setId(UUID.randomUUID().toString());
            }
            
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(sceneSubmission.getId()));
            
            RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKeyBuilder.build());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = objectMapper.convertValue(sceneSubmission, Map.class);
            dataMap.remove("id");
            
            for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                if (entry.getValue() != null) {
                    addColumn(rowPutChange, entry.getKey(), entry.getValue());
                }
            }
            
            tablestoreClient.putRow(new PutRowRequest(rowPutChange));
            return sceneSubmission.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save scene submission", e);
        }
    }
    
    @Override
    public SceneSubmission findById(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            SingleRowQueryCriteria criteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKeyBuilder.build());
            criteria.setMaxVersions(1);
            
            GetRowResponse response = tablestoreClient.getRow(new GetRowRequest(criteria));
            Row row = response.getRow();
            
            if (row != null) {
                return rowToSceneSubmission(row);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find scene submission", e);
        }
    }
    
    @Override
    public void update(SceneSubmission sceneSubmission) {
        if (sceneSubmission.getId() != null) {
            sceneSubmission.setLastUpdatedAt(new Date());
            save(sceneSubmission);
        }
    }
    
    @Override
    public void delete(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            RowDeleteChange rowDeleteChange = new RowDeleteChange(TABLE_NAME, primaryKeyBuilder.build());
            tablestoreClient.deleteRow(new DeleteRowRequest(rowDeleteChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete scene submission", e);
        }
    }
    
    @Override
    public List<SceneSubmission> findByTemplateId(String templateId) {
        return queryByIndex("templateId_index", "templateId", templateId);
    }
    
    @Override
    public List<SceneSubmission> findByUserId(String userId) {
        return queryByIndex("userId_index", "userId", userId);
    }
    
    @Override
    public List<SceneSubmission> findByTemplateIdAndUserId(String templateId, String userId) {
        // TableStore doesn't support multi-field queries easily
        // Workaround: Get by templateId, filter by userId
        List<SceneSubmission> submissions = findByTemplateId(templateId);
        submissions.removeIf(s -> !userId.equals(s.getUserId()));
        return submissions;
    }
    
    @Override
    public List<SceneSubmission> findByStatus(String status) {
        return queryByIndex("status_index", "status", status);
    }
    
    @Override
    public List<SceneSubmission> findByTemplateIdAndStatus(String templateId, String status) {
        List<SceneSubmission> submissions = findByTemplateId(templateId);
        submissions.removeIf(s -> !status.equals(s.getStatus()));
        return submissions;
    }
    
    @Override
    public String getSignedUrl(String videoUrl) throws Exception {
        if (ossStorageService == null || videoUrl == null) {
            return videoUrl;
        }
        return ossStorageService.generateSignedUrl(videoUrl);
    }
    
    private List<SceneSubmission> queryByIndex(String indexName, String columnName, String value) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(100);
            // criteria.setIndexName(indexName); // TODO: Tablestore SDK does not support setIndexName on RangeRowQueryCriteria
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn(columnName, PrimaryKeyValue.fromString(value));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn(columnName, PrimaryKeyValue.fromString(value));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<SceneSubmission> results = new ArrayList<>();
            for (Row row : response.getRows()) {
                results.add(rowToSceneSubmission(row));
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query by index: " + indexName, e);
        }
    }
    
    private List<SceneSubmission> queryAllScenes() {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(1000); // Reasonable limit
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("id", PrimaryKeyValue.INF_MIN);
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("id", PrimaryKeyValue.INF_MAX);
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<SceneSubmission> results = new ArrayList<>();
            for (Row row : response.getRows()) {
                results.add(rowToSceneSubmission(row));
            }
            return results;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    private SceneSubmission rowToSceneSubmission(Row row) {
        try {
            Map<String, Object> dataMap = new HashMap<>();
            
            for (PrimaryKeyColumn column : row.getPrimaryKey().getPrimaryKeyColumns()) {
                dataMap.put(column.getName(), column.getValue().asString());
            }
            
            for (Column column : row.getColumns()) {
                String columnName = column.getName();
                ColumnValue columnValue = column.getValue();
                
                if (columnValue.getType() == ColumnType.STRING) {
                    dataMap.put(columnName, columnValue.asString());
                } else if (columnValue.getType() == ColumnType.INTEGER) {
                    dataMap.put(columnName, columnValue.asLong());
                } else if (columnValue.getType() == ColumnType.BOOLEAN) {
                    dataMap.put(columnName, columnValue.asBoolean());
                }
            }
            
            return objectMapper.convertValue(dataMap, SceneSubmission.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert row to SceneSubmission", e);
        }
    }
    
    private void addColumn(RowPutChange rowPutChange, String name, Object value) {
        if (value instanceof String) {
            rowPutChange.addColumn(name, ColumnValue.fromString((String) value));
        } else if (value instanceof Long) {
            rowPutChange.addColumn(name, ColumnValue.fromLong((Long) value));
        } else if (value instanceof Integer) {
            rowPutChange.addColumn(name, ColumnValue.fromLong(((Integer) value).longValue()));
        } else if (value instanceof Boolean) {
            rowPutChange.addColumn(name, ColumnValue.fromBoolean((Boolean) value));
        } else {
            try {
                String jsonValue = objectMapper.writeValueAsString(value);
                rowPutChange.addColumn(name, ColumnValue.fromString(jsonValue));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize column: " + name, e);
            }
        }
    }
    
    @Override
    public void deleteScenesByUserId(String userId) {
        List<SceneSubmission> scenes = findByUserId(userId);
        for (SceneSubmission scene : scenes) {
            delete(scene.getId());
        }
    }
    
    @Override
    public void deleteScenesByTemplateId(String templateId) {
        List<SceneSubmission> scenes = findByTemplateId(templateId);
        for (SceneSubmission scene : scenes) {
            delete(scene.getId());
        }
    }
    
    @Override
    public List<SceneSubmission> findTopPerformingScenes(String templateId, int limit) {
        List<SceneSubmission> scenes = findByTemplateId(templateId);
        scenes.sort((a, b) -> {
            Double scoreA = a.getSimilarityScore() != null ? a.getSimilarityScore() : 0.0;
            Double scoreB = b.getSimilarityScore() != null ? b.getSimilarityScore() : 0.0;
            return scoreB.compareTo(scoreA); // Descending order
        });
        return scenes.size() > limit ? scenes.subList(0, limit) : scenes;
    }
    
    @Override
    public double getAverageSimilarityScore(String templateId) {
        List<SceneSubmission> scenes = findByTemplateId(templateId);
        if (scenes.isEmpty()) return 0.0;
        
        double sum = 0.0;
        int count = 0;
        for (SceneSubmission scene : scenes) {
            if (scene.getSimilarityScore() != null) {
                sum += scene.getSimilarityScore();
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }
    
    @Override
    public List<SceneSubmission> findSubmissionsByDateRange(java.util.Date startDate, java.util.Date endDate) {
        // Query all and filter by date range
        List<SceneSubmission> allScenes = queryAllScenes();
        List<SceneSubmission> result = new ArrayList<>();
        for (SceneSubmission scene : allScenes) {
            if (scene.getSubmittedAt() != null) {
                if ((startDate == null || !scene.getSubmittedAt().before(startDate)) &&
                    (endDate == null || !scene.getSubmittedAt().after(endDate))) {
                    result.add(scene);
                }
            }
        }
        return result;
    }
    
    @Override
    public List<SceneSubmission> getApprovedScenesInOrder(String templateId, String userId) {
        List<SceneSubmission> scenes = findApprovedScenesByTemplateIdAndUserId(templateId, userId);
        scenes.sort((a, b) -> Integer.compare(a.getSceneNumber(), b.getSceneNumber()));
        return scenes;
    }
    
    @Override
    public boolean areAllScenesApproved(String templateId, String userId, int totalScenes) {
        List<SceneSubmission> approved = findApprovedScenesByTemplateIdAndUserId(templateId, userId);
        return approved.size() >= totalScenes;
    }
    
    @Override
    public SceneSubmission findLatestSubmissionForScene(String templateId, String userId, int sceneNumber) {
        List<SceneSubmission> scenes = findByTemplateIdAndUserId(templateId, userId);
        SceneSubmission latest = null;
        for (SceneSubmission scene : scenes) {
            if (scene.getSceneNumber() == sceneNumber) {
                if (latest == null || (scene.getSubmittedAt() != null && 
                    (latest.getSubmittedAt() == null || scene.getSubmittedAt().after(latest.getSubmittedAt())))) {
                    latest = scene;
                }
            }
        }
        return latest;
    }
    
    @Override
    public List<SceneSubmission> findResubmissionHistory(String originalSceneId) {
        SceneSubmission original = findById(originalSceneId);
        if (original == null || original.getResubmissionHistory() == null) {
            return new ArrayList<>();
        }
        
        List<SceneSubmission> history = new ArrayList<>();
        for (String sceneId : original.getResubmissionHistory()) {
            SceneSubmission scene = findById(sceneId);
            if (scene != null) {
                history.add(scene);
            }
        }
        return history;
    }
    
    @Override
    public List<SceneSubmission> findRecentSubmissions(int limit) {
        List<SceneSubmission> allScenes = queryAllScenes();
        allScenes.sort((a, b) -> {
            if (a.getSubmittedAt() == null) return 1;
            if (b.getSubmittedAt() == null) return -1;
            return b.getSubmittedAt().compareTo(a.getSubmittedAt()); // Descending
        });
        return allScenes.size() > limit ? allScenes.subList(0, limit) : allScenes;
    }
    
    @Override
    public List<SceneSubmission> findSubmissionsByReviewer(String reviewerId) {
        List<SceneSubmission> allScenes = queryAllScenes();
        List<SceneSubmission> result = new ArrayList<>();
        for (SceneSubmission scene : allScenes) {
            if (reviewerId.equals(scene.getReviewedBy())) {
                result.add(scene);
            }
        }
        return result;
    }
    
    @Override
    public List<SceneSubmission> findPendingSubmissionsForReview() {
        return findByStatus("pending");
    }
    
    @Override
    public int countRejectedScenesByTemplateIdAndUserId(String templateId, String userId) {
        return findRejectedScenesByTemplateIdAndUserId(templateId, userId).size();
    }
    
    @Override
    public int countPendingScenesByTemplateIdAndUserId(String templateId, String userId) {
        return findPendingScenesByTemplateIdAndUserId(templateId, userId).size();
    }
    
    @Override
    public int countApprovedScenesByTemplateIdAndUserId(String templateId, String userId) {
        return findApprovedScenesByTemplateIdAndUserId(templateId, userId).size();
    }
    
    @Override
    public int countScenesByTemplateIdAndUserId(String templateId, String userId) {
        return findByTemplateIdAndUserId(templateId, userId).size();
    }
    
    @Override
    public List<SceneSubmission> findRejectedScenesByTemplateIdAndUserId(String templateId, String userId) {
        List<SceneSubmission> scenes = findByTemplateIdAndUserId(templateId, userId);
        scenes.removeIf(s -> !"rejected".equals(s.getStatus()));
        return scenes;
    }
    
    @Override
    public List<SceneSubmission> findPendingScenesByTemplateIdAndUserId(String templateId, String userId) {
        List<SceneSubmission> scenes = findByTemplateIdAndUserId(templateId, userId);
        scenes.removeIf(s -> !"pending".equals(s.getStatus()));
        return scenes;
    }
    
    @Override
    public List<SceneSubmission> findApprovedScenesByTemplateIdAndUserId(String templateId, String userId) {
        List<SceneSubmission> scenes = findByTemplateIdAndUserId(templateId, userId);
        scenes.removeIf(s -> !"approved".equals(s.getStatus()));
        return scenes;
    }
    
    @Override
    public SceneSubmission findByTemplateIdAndUserIdAndSceneNumber(String templateId, String userId, int sceneNumber) {
        List<SceneSubmission> scenes = findByTemplateIdAndUserId(templateId, userId);
        for (SceneSubmission scene : scenes) {
            if (scene.getSceneNumber() == sceneNumber) {
                return scene;
            }
        }
        return null;
    }
    
    @Override
    public SceneSubmission uploadAndSaveScene(org.springframework.web.multipart.MultipartFile file, String assignmentId, String userId, int sceneNumber, String sceneTitle) throws Exception {
        // Create scene submission object
        SceneSubmission scene = new SceneSubmission();
        scene.setId(UUID.randomUUID().toString());
        scene.setTemplateId(assignmentId);  // templateId stores the assignment ID
        scene.setUserId(userId);
        scene.setSceneNumber(sceneNumber);
        scene.setSceneTitle(sceneTitle);
        scene.setStatus("pending");
        scene.setSubmittedAt(new Date());
        
        // Upload video to OSS if storage service is available
        if (ossStorageService != null) {
            try {
                String videoPath = "scenes/" + userId + "/" + assignmentId + "/scene_" + sceneNumber + "_" + scene.getId();
                // Convert MultipartFile to InputStream and upload
                String contentType = file.getContentType() != null ? file.getContentType() : "video/mp4";
                String videoUrl = ossStorageService.uploadFile(file.getInputStream(), videoPath, contentType);
                scene.setVideoUrl(videoUrl);
                
                // TODO: Extract thumbnail from video
                // String thumbnailUrl = ossStorageService.extractThumbnail(videoUrl);
                // scene.setThumbnailUrl(thumbnailUrl);
            } catch (Exception e) {
                throw new Exception("Failed to upload scene video: " + e.getMessage(), e);
            }
        }
        
        // Save to Tablestore
        save(scene);
        return scene;
    }
}
