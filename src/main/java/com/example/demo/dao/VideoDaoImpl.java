package com.example.demo.dao;

import com.example.demo.model.Video;
import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class VideoDaoImpl implements VideoDao {
    @Autowired
    private SyncClient tablestoreClient;
    
    @Autowired(required = false)
    private com.example.demo.service.AlibabaOssStorageService ossStorageService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = "videos";

    @Override
    public Video saveVideo(Video video) {
        try {
            if (video.getId() == null || video.getId().isEmpty()) {
                video.setId(UUID.randomUUID().toString());
            }
            
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(video.getId()));
            
            RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKeyBuilder.build());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> videoMap = objectMapper.convertValue(video, Map.class);
            videoMap.remove("id");
            
            for (Map.Entry<String, Object> entry : videoMap.entrySet()) {
                if (entry.getValue() != null) {
                    addColumn(rowPutChange, entry.getKey(), entry.getValue());
                }
            }
            
            tablestoreClient.putRow(new PutRowRequest(rowPutChange));
            return video;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save video", e);
        }
    }

    @Override
    public Video getVideoById(String videoId) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(videoId));
            
            SingleRowQueryCriteria criteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKeyBuilder.build());
            criteria.setMaxVersions(1);
            
            GetRowResponse response = tablestoreClient.getRow(new GetRowRequest(criteria));
            Row row = response.getRow();
            
            if (row != null) {
                return rowToVideo(row);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get video by id", e);
        }
    }

    @Override
    public void updateVideo(Video video) {
        saveVideo(video);
    }

    @Override
    public Video saveVideoWithTemplate(Video video, String templateId) {
        try {
            video.setId(UUID.randomUUID().toString());
            video.setTemplateId(templateId);
            return saveVideo(video);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save video with template", e);
        }
    }

    @Override
    public boolean deleteVideoById(String videoId) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(videoId));
            
            RowDeleteChange rowDeleteChange = new RowDeleteChange(TABLE_NAME, primaryKeyBuilder.build());
            tablestoreClient.deleteRow(new DeleteRowRequest(rowDeleteChange));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public Video uploadAndSaveVideo(org.springframework.web.multipart.MultipartFile file, String userId, String videoId) throws Exception {
        if (ossStorageService == null) {
            throw new IllegalStateException("AlibabaOssStorageService not available");
        }
        
        try {
            System.out.println("[VIDEO-UPLOAD] Starting upload for videoId: " + videoId);
            System.out.println("[VIDEO-UPLOAD] File: " + file.getOriginalFilename() + ", Size: " + file.getSize() + " bytes");
            
            com.example.demo.service.AlibabaOssStorageService.UploadResult uploadResult = 
                ossStorageService.uploadVideoWithThumbnail(file, userId, videoId);
            System.out.println("[VIDEO-UPLOAD] ✅ Upload complete: " + uploadResult.videoUrl);
            
            long durationSeconds = 0;
            java.io.File tempFile = null;
            try {
                System.out.println("[VIDEO-DURATION] Downloading video for duration extraction...");
                tempFile = ossStorageService.downloadToTempFile(uploadResult.videoUrl, "video_duration_", ".mp4");
                
                ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", tempFile.getAbsolutePath()
                );
                Process process = pb.start();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
                );
                String durationStr = reader.readLine();
                int exitCode = process.waitFor();
                
                if (exitCode == 0 && durationStr != null && !durationStr.isEmpty()) {
                    durationSeconds = (long) Double.parseDouble(durationStr);
                    System.out.println("[VIDEO-DURATION] ✅ Extracted duration: " + durationSeconds + " seconds");
                } else {
                    System.err.println("[VIDEO-DURATION] ⚠️ FFprobe failed (exit code: " + exitCode + ")");
                }
            } catch (Exception e) {
                System.err.println("[VIDEO-DURATION] ❌ Failed to extract duration: " + e.getMessage());
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
            
            Video video = new Video();
            video.setId(videoId);
            video.setUserId(userId);
            video.setUrl(uploadResult.videoUrl);
            video.setThumbnailUrl(uploadResult.thumbnailUrl);
            video.setDurationSeconds(durationSeconds);
            
            System.out.println("[VIDEO-UPLOAD] Saving video to TableStore...");
            Video savedVideo = saveVideo(video);
            System.out.println("[VIDEO-UPLOAD] ✅ Video saved successfully");
            
            return savedVideo;
        } catch (Exception e) {
            System.err.println("[VIDEO-UPLOAD] ❌ Upload failed: " + e.getMessage());
            throw e;
        }
    }
    
    @Override
    public String getSignedUrl(String videoUrl) throws Exception {
        if (ossStorageService == null || videoUrl == null) {
            return videoUrl;
        }
        return ossStorageService.generateSignedUrl(videoUrl);
    }
    
    private Video rowToVideo(Row row) {
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
            
            return objectMapper.convertValue(dataMap, Video.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert row to Video", e);
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
