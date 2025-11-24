package com.example.demo.dao;

import com.example.demo.model.TemplateFolder;
import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class TemplateFolderDaoImpl implements TemplateFolderDao {
    
    @Autowired
    private SyncClient tablestoreClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = "templateFolders";
    
    @Override
    public String create(TemplateFolder folder) {
        try {
            if (folder.getId() == null) {
                folder.setId(UUID.randomUUID().toString());
            }
            
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(folder.getId()));
            
            RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKeyBuilder.build());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = objectMapper.convertValue(folder, Map.class);
            dataMap.remove("id");
            
            for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                if (entry.getValue() != null) {
                    addColumn(rowPutChange, entry.getKey(), entry.getValue());
                }
            }
            
            tablestoreClient.putRow(new PutRowRequest(rowPutChange));
            return folder.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create folder", e);
        }
    }
    
    @Override
    public TemplateFolder findById(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            SingleRowQueryCriteria criteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKeyBuilder.build());
            criteria.setMaxVersions(1);
            
            GetRowResponse response = tablestoreClient.getRow(new GetRowRequest(criteria));
            Row row = response.getRow();
            
            if (row != null) {
                return rowToFolder(row);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find folder", e);
        }
    }
    
    @Override
    public List<TemplateFolder> findByUserId(String userId) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(100);
            criteria.setIndexName("userId_index");
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("userId", PrimaryKeyValue.fromString(userId));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("userId", PrimaryKeyValue.fromString(userId));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<TemplateFolder> results = new ArrayList<>();
            for (Row row : response.getRows()) {
                results.add(rowToFolder(row));
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find folders by userId", e);
        }
    }
    
    @Override
    public void update(TemplateFolder folder) {
        create(folder);
    }
    
    @Override
    public void delete(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            RowDeleteChange rowDeleteChange = new RowDeleteChange(TABLE_NAME, primaryKeyBuilder.build());
            tablestoreClient.deleteRow(new DeleteRowRequest(rowDeleteChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete folder", e);
        }
    }
    
    private TemplateFolder rowToFolder(Row row) {
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
            
            return objectMapper.convertValue(dataMap, TemplateFolder.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert row to TemplateFolder", e);
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
