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
            criteria.setIndexName(indexName);
            
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
}
