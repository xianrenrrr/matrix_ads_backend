package com.example.demo.dao;

import com.example.demo.model.Group;
import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class GroupDaoImpl implements GroupDao {
    
    @Autowired
    private SyncClient tablestoreClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = "groups";

    @Override
    public void save(Group group) {
        try {
            if (group.getId() == null || group.getId().isEmpty()) {
                group.setId(UUID.randomUUID().toString());
            }
            
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(group.getId()));
            
            RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKeyBuilder.build());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> groupMap = objectMapper.convertValue(group, Map.class);
            groupMap.remove("id");
            
            for (Map.Entry<String, Object> entry : groupMap.entrySet()) {
                if (entry.getValue() != null) {
                    addColumn(rowPutChange, entry.getKey(), entry.getValue());
                }
            }
            
            tablestoreClient.putRow(new PutRowRequest(rowPutChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save group", e);
        }
    }

    @Override
    public void update(Group group) {
        if (group.getId() == null || group.getId().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty for update");
        }
        save(group);
    }

    @Override
    public Group findByToken(String token) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(1);
            // criteria.setIndexName("token_index"); // TODO: Tablestore SDK does not support setIndexName on RangeRowQueryCriteria
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("token", PrimaryKeyValue.fromString(token));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("token", PrimaryKeyValue.fromString(token));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            if (!response.getRows().isEmpty()) {
                return rowToGroup(response.getRows().get(0));
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch group by token", e);
        }
    }

    @Override
    public Group findById(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            SingleRowQueryCriteria criteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKeyBuilder.build());
            criteria.setMaxVersions(1);
            
            GetRowResponse response = tablestoreClient.getRow(new GetRowRequest(criteria));
            Row row = response.getRow();
            
            if (row != null) {
                return rowToGroup(row);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch group by ID", e);
        }
    }

    @Override
    public List<Group> findByManagerId(String managerId) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(100);
            // criteria.setIndexName("managerId_index"); // TODO: Tablestore SDK does not support setIndexName on RangeRowQueryCriteria
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("managerId", PrimaryKeyValue.fromString(managerId));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("managerId", PrimaryKeyValue.fromString(managerId));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<Group> groups = new ArrayList<>();
            for (Row row : response.getRows()) {
                groups.add(rowToGroup(row));
            }
            return groups;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch groups by manager ID", e);
        }
    }

    @Override
    public List<Group> findByStatus(String status) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(100);
            // criteria.setIndexName("status_index"); // TODO: Tablestore SDK does not support setIndexName on RangeRowQueryCriteria
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("status", PrimaryKeyValue.fromString(status));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("status", PrimaryKeyValue.fromString(status));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<Group> groups = new ArrayList<>();
            for (Row row : response.getRows()) {
                groups.add(rowToGroup(row));
            }
            return groups;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch groups by status", e);
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
            throw new RuntimeException("Failed to delete group", e);
        }
    }

    @Override
    public void updateStatus(String id, String status) {
        try {
            Group group = findById(id);
            if (group != null) {
                group.setStatus(status);
                save(group);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update group status", e);
        }
    }
    
    @Override
    public String getUserGroupId(String userId) {
        // TableStore doesn't support complex queries easily
        // This would need a secondary index or different approach
        throw new UnsupportedOperationException("getUserGroupId not implemented for TableStore yet");
    }
    
    @Override
    public void addTemplateToGroup(String groupId, String templateId) {
        try {
            Group group = findById(groupId);
            if (group == null) {
                throw new RuntimeException("Group " + groupId + " does not exist");
            }
            
            if (group.getAssignedTemplates() == null) {
                group.setAssignedTemplates(new ArrayList<>());
            }
            
            if (!group.getAssignedTemplates().contains(templateId)) {
                group.getAssignedTemplates().add(templateId);
                save(group);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to add template to group", e);
        }
    }
    
    @Override
    public void removeTemplateFromGroup(String groupId, String templateId) {
        try {
            Group group = findById(groupId);
            if (group != null && group.getAssignedTemplates() != null) {
                group.getAssignedTemplates().remove(templateId);
                save(group);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove template from group", e);
        }
    }
    
    private Group rowToGroup(Row row) {
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
            
            return objectMapper.convertValue(dataMap, Group.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert row to Group", e);
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
