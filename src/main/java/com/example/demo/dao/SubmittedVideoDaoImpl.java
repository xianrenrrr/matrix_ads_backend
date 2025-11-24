package com.example.demo.dao;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.*;
import com.example.demo.model.SubmittedVideo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class SubmittedVideoDaoImpl implements SubmittedVideoDao {
    
    @Autowired
    private SyncClient tablestoreClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = "submittedVideos";
    
    @Override
    public String save(SubmittedVideo video) {
        try {
            if (video.getId() == null) {
                video.setId(UUID.randomUUID().toString());
            }
            
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(video.getId()));
            
            RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKeyBuilder.build());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = objectMapper.convertValue(video, Map.class);
            dataMap.remove("id");
            
            for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                if (entry.getValue() != null) {
                    addColumn(rowPutChange, entry.getKey(), entry.getValue());
                }
            }
            
            tablestoreClient.putRow(new PutRowRequest(rowPutChange));
            return video.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save submitted video", e);
        }
    }
    
    @Override
    public SubmittedVideo findById(String compositeVideoId) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(compositeVideoId));
            
            SingleRowQueryCriteria criteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKeyBuilder.build());
            criteria.setMaxVersions(1);
            
            GetRowResponse response = tablestoreClient.getRow(new GetRowRequest(criteria));
            Row row = response.getRow();
            
            if (row != null) {
                return rowToSubmittedVideo(row);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find submitted video", e);
        }
    }
    
    @Override
    public void update(SubmittedVideo video) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(video.getId()));
            
            RowUpdateChange rowUpdateChange = new RowUpdateChange(TABLE_NAME, primaryKeyBuilder.build());
            
            if (video.getUploadedBy() != null) {
                rowUpdateChange.put("uploadedBy", ColumnValue.fromString(video.getUploadedBy()));
            }
            if (video.getAssignmentId() != null) {
                rowUpdateChange.put("assignmentId", ColumnValue.fromString(video.getAssignmentId()));
            }
            if (video.getScenes() != null) {
                String scenesJson = objectMapper.writeValueAsString(video.getScenes());
                rowUpdateChange.put("scenes", ColumnValue.fromString(scenesJson));
            }
            if (video.getProgress() != null) {
                String progressJson = objectMapper.writeValueAsString(video.getProgress());
                rowUpdateChange.put("progress", ColumnValue.fromString(progressJson));
            }
            if (video.getPublishStatus() != null) {
                rowUpdateChange.put("publishStatus", ColumnValue.fromString(video.getPublishStatus()));
            }
            if (video.getApprovedAt() != null) {
                rowUpdateChange.put("approvedAt", ColumnValue.fromLong(video.getApprovedAt().getTime()));
            }
            if (video.getLastUpdated() != null) {
                rowUpdateChange.put("lastUpdated", ColumnValue.fromLong(video.getLastUpdated().getTime()));
            }
            if (video.getDownloadedBy() != null) {
                rowUpdateChange.put("downloadedBy", ColumnValue.fromString(video.getDownloadedBy()));
            }
            if (video.getDownloadedAt() != null) {
                rowUpdateChange.put("downloadedAt", ColumnValue.fromLong(video.getDownloadedAt().getTime()));
            }
            if (video.getCompiledVideoUrl() != null) {
                rowUpdateChange.put("compiledVideoUrl", ColumnValue.fromString(video.getCompiledVideoUrl()));
            }
            
            tablestoreClient.updateRow(new UpdateRowRequest(rowUpdateChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to update submitted video", e);
        }
    }
    
    @Override
    public List<SubmittedVideo> findByAssignmentIds(List<String> assignmentIds) {
        // TableStore: scan table and filter by assignmentId
        // In production, use a secondary index on assignmentId for better performance
        List<SubmittedVideo> results = new ArrayList<>();
        
        // For now, we'll scan the table and filter (not optimal but works)
        // In production, you'd want to use a secondary index on assignmentId
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setInclusiveStartPrimaryKey(PrimaryKeyBuilder.createPrimaryKeyBuilder()
                .addPrimaryKeyColumn("id", PrimaryKeyValue.INF_MIN)
                .build());
            criteria.setExclusiveEndPrimaryKey(PrimaryKeyBuilder.createPrimaryKeyBuilder()
                .addPrimaryKeyColumn("id", PrimaryKeyValue.INF_MAX)
                .build());
            criteria.setMaxVersions(1);
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            for (Row row : response.getRows()) {
                SubmittedVideo video = rowToSubmittedVideo(row);
                if (video != null && assignmentIds.contains(video.getAssignmentId())) {
                    results.add(video);
                }
            }
            
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query submitted videos", e);
        }
    }
    
    @Override
    public List<SubmittedVideo> findByUserId(String userId) {
        // Scan table and filter by uploadedBy field
        List<SubmittedVideo> results = new ArrayList<>();
        
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setInclusiveStartPrimaryKey(PrimaryKeyBuilder.createPrimaryKeyBuilder()
                .addPrimaryKeyColumn("id", PrimaryKeyValue.INF_MIN)
                .build());
            criteria.setExclusiveEndPrimaryKey(PrimaryKeyBuilder.createPrimaryKeyBuilder()
                .addPrimaryKeyColumn("id", PrimaryKeyValue.INF_MAX)
                .build());
            criteria.setMaxVersions(1);
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            for (Row row : response.getRows()) {
                SubmittedVideo video = rowToSubmittedVideo(row);
                if (video != null && userId.equals(video.getUploadedBy())) {
                    results.add(video);
                }
            }
            
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query submitted videos by userId", e);
        }
    }
    
    private SubmittedVideo rowToSubmittedVideo(Row row) {
        try {
            SubmittedVideo video = new SubmittedVideo();
            
            for (PrimaryKeyColumn column : row.getPrimaryKey().getPrimaryKeyColumns()) {
                if ("id".equals(column.getName())) {
                    video.setId(column.getValue().asString());
                }
            }
            
            for (Column column : row.getColumns()) {
                String columnName = column.getName();
                ColumnValue columnValue = column.getValue();
                
                switch (columnName) {
                    case "uploadedBy":
                        video.setUploadedBy(columnValue.asString());
                        break;
                    case "assignmentId":
                        video.setAssignmentId(columnValue.asString());
                        break;
                    case "scenes":
                        @SuppressWarnings("unchecked")
                        Map<String, Object> scenes = objectMapper.readValue(columnValue.asString(), Map.class);
                        video.setScenes(scenes);
                        break;
                    case "progress":
                        @SuppressWarnings("unchecked")
                        Map<String, Object> progress = objectMapper.readValue(columnValue.asString(), Map.class);
                        video.setProgress(progress);
                        break;
                    case "publishStatus":
                        video.setPublishStatus(columnValue.asString());
                        break;
                    case "approvedAt":
                        video.setApprovedAt(new Date(columnValue.asLong()));
                        break;
                    case "lastUpdated":
                        video.setLastUpdated(new Date(columnValue.asLong()));
                        break;
                    case "createdAt":
                        video.setCreatedAt(new Date(columnValue.asLong()));
                        break;
                    case "downloadedBy":
                        video.setDownloadedBy(columnValue.asString());
                        break;
                    case "downloadedAt":
                        video.setDownloadedAt(new Date(columnValue.asLong()));
                        break;
                    case "compiledVideoUrl":
                        video.setCompiledVideoUrl(columnValue.asString());
                        break;
                }
            }
            
            return video;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert row to SubmittedVideo", e);
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
        } else if (value instanceof Date) {
            rowPutChange.addColumn(name, ColumnValue.fromLong(((Date) value).getTime()));
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
