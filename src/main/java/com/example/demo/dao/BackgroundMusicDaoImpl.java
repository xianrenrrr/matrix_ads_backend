package com.example.demo.dao;

import com.example.demo.model.BackgroundMusic;
import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class BackgroundMusicDaoImpl implements BackgroundMusicDao {
    
    @Autowired
    private SyncClient tablestoreClient;
    
    @Autowired(required = false)
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = "backgroundMusic";
    
    @Override
    public String saveBackgroundMusic(BackgroundMusic bgm) {
        try {
            if (bgm.getId() == null) {
                bgm.setId(UUID.randomUUID().toString());
            }
            
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(bgm.getId()));
            
            RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKeyBuilder.build());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = objectMapper.convertValue(bgm, Map.class);
            dataMap.remove("id");
            
            for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                if (entry.getValue() != null) {
                    addColumn(rowPutChange, entry.getKey(), entry.getValue());
                }
            }
            
            tablestoreClient.putRow(new PutRowRequest(rowPutChange));
            return bgm.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save background music", e);
        }
    }
    
    @Override
    public BackgroundMusic getBackgroundMusic(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            SingleRowQueryCriteria criteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKeyBuilder.build());
            criteria.setMaxVersions(1);
            
            GetRowResponse response = tablestoreClient.getRow(new GetRowRequest(criteria));
            Row row = response.getRow();
            
            if (row != null) {
                return rowToBGM(row);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find background music", e);
        }
    }
    
    @Override
    public List<BackgroundMusic> getBackgroundMusicByUserId(String userId) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(100);
            // criteria.setIndexName("userId_index"); // TODO: Tablestore SDK does not support setIndexName on RangeRowQueryCriteria
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("userId", PrimaryKeyValue.fromString(userId));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("userId", PrimaryKeyValue.fromString(userId));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<BackgroundMusic> results = new ArrayList<>();
            for (Row row : response.getRows()) {
                results.add(rowToBGM(row));
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find background music by userId", e);
        }
    }
    
    public void delete(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            RowDeleteChange rowDeleteChange = new RowDeleteChange(TABLE_NAME, primaryKeyBuilder.build());
            tablestoreClient.deleteRow(new DeleteRowRequest(rowDeleteChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete background music", e);
        }
    }
    
    private BackgroundMusic rowToBGM(Row row) {
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
            
            return objectMapper.convertValue(dataMap, BackgroundMusic.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert row to BackgroundMusic", e);
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
    public BackgroundMusic uploadAndSaveBackgroundMusic(org.springframework.web.multipart.MultipartFile file, String userId, String title, String description) throws Exception {
        // Create BackgroundMusic object
        BackgroundMusic bgm = new BackgroundMusic();
        bgm.setId(UUID.randomUUID().toString());
        bgm.setUserId(userId);
        bgm.setTitle(title);
        bgm.setDescription(description);
        bgm.setUploadedAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        
        // Upload file to OSS if storage service is available
        if (ossStorageService != null) {
            try {
                String audioPath = "bgm/" + userId + "/" + bgm.getId() + "_" + file.getOriginalFilename();
                String contentType = file.getContentType() != null ? file.getContentType() : "audio/mpeg";
                String audioUrl = ossStorageService.uploadFile(file.getInputStream(), audioPath, contentType);
                bgm.setAudioUrl(audioUrl);
                
                // TODO: Calculate audio duration if needed
                // bgm.setDurationSeconds(calculateDuration(file));
            } catch (Exception e) {
                throw new Exception("Failed to upload background music: " + e.getMessage(), e);
            }
        }
        
        // Save to Tablestore
        saveBackgroundMusic(bgm);
        return bgm;
    }
    
    @Override
    public boolean deleteBackgroundMusic(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            RowDeleteChange rowDeleteChange = new RowDeleteChange(TABLE_NAME, primaryKeyBuilder.build());
            tablestoreClient.deleteRow(new DeleteRowRequest(rowDeleteChange));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete background music", e);
        }
    }
}
