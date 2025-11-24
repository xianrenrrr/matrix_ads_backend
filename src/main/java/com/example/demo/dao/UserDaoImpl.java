package com.example.demo.dao;

import com.example.demo.model.User;
import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class UserDaoImpl implements UserDao {
    @Autowired
    private SyncClient tablestoreClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = "users";

    @Override
    public User findByUsername(String username) {
        try {
            // Use secondary index to query by username
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(1);
            criteria.setIndexName("username_index");
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("username", PrimaryKeyValue.fromString(username));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("username", PrimaryKeyValue.fromString(username));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            if (!response.getRows().isEmpty()) {
                return rowToUser(response.getRows().get(0));
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user by username", e);
        }
    }

    @Override
    public User findByEmail(String email) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(1);
            criteria.setIndexName("email_index");
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("email", PrimaryKeyValue.fromString(email));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("email", PrimaryKeyValue.fromString(email));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            if (!response.getRows().isEmpty()) {
                return rowToUser(response.getRows().get(0));
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user by email", e);
        }
    }

    @Override
    public User findByEmailAndRole(String email, String role) {
        // TableStore doesn't support multi-field queries easily
        // Workaround: Get by email first, then filter by role
        User user = findByEmail(email);
        if (user != null && role.equals(user.getRole())) {
            return user;
        }
        return null;
    }

    @Override
    public User findByPhone(String phone) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(1);
            criteria.setIndexName("phone_index");
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("phone", PrimaryKeyValue.fromString(phone));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("phone", PrimaryKeyValue.fromString(phone));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            if (!response.getRows().isEmpty()) {
                return rowToUser(response.getRows().get(0));
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user by phone", e);
        }
    }

    @Override
    public User findById(String id) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(id));
            
            SingleRowQueryCriteria criteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKeyBuilder.build());
            criteria.setMaxVersions(1);
            
            GetRowResponse response = tablestoreClient.getRow(new GetRowRequest(criteria));
            Row row = response.getRow();
            
            if (row != null) {
                return rowToUser(row);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user by id", e);
        }
    }

    @Override
    public void save(User user) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(user.getId()));
            
            RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKeyBuilder.build());
            
            // Convert user to map and add columns
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = objectMapper.convertValue(user, Map.class);
            userMap.remove("id"); // Remove id as it's the primary key
            
            for (Map.Entry<String, Object> entry : userMap.entrySet()) {
                if (entry.getValue() != null) {
                    addColumn(rowPutChange, entry.getKey(), entry.getValue());
                }
            }
            
            tablestoreClient.putRow(new PutRowRequest(rowPutChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save user", e);
        }
    }

    @Override
    public void addCreatedTemplate(String userId, String templateId) {
        try {
            User user = findById(userId);
            if (user != null) {
                if (user.getCreated_Templates() == null) {
                    user.setCreated_Templates(new HashMap<>());
                }
                user.getCreated_Templates().put(templateId, true);
                save(user);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to add created template", e);
        }
    }

    @Override
    public void removeCreatedTemplate(String userId, String templateId) {
        try {
            User user = findById(userId);
            if (user != null && user.getCreated_Templates() != null) {
                user.getCreated_Templates().remove(templateId);
                save(user);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove created template", e);
        }
    }

    @Override
    public Map<String, Boolean> getCreatedTemplates(String userId) {
        try {
            User user = findById(userId);
            if (user != null && user.getCreated_Templates() != null) {
                return user.getCreated_Templates();
            }
            return new HashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get created templates", e);
        }
    }

    @Override
    public void addSubscribedTemplate(String userId, String templateId) {
        // Similar to addCreatedTemplate - implement if needed
        throw new UnsupportedOperationException("Not implemented for TableStore yet");
    }

    @Override
    public void removeSubscribedTemplate(String userId, String templateId) {
        throw new UnsupportedOperationException("Not implemented for TableStore yet");
    }

    @Override
    public Map<String, Boolean> getSubscribedTemplates(String userId) {
        throw new UnsupportedOperationException("Not implemented for TableStore yet");
    }
    
    @Override
    public List<User> findByRole(String role) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(100);
            criteria.setIndexName("role_index");
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("role", PrimaryKeyValue.fromString(role));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("role", PrimaryKeyValue.fromString(role));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<User> users = new ArrayList<>();
            for (Row row : response.getRows()) {
                users.add(rowToUser(row));
            }
            return users;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find users by role", e);
        }
    }
    
    public String createUser(User user) {
        if (user.getId() == null || user.getId().isEmpty()) {
            user.setId(UUID.randomUUID().toString());
        }
        save(user);
        return user.getId();
    }
    
    public User authenticateUser(String username, String password) {
        try {
            User user = findByUsername(username);
            if (user == null) {
                return null;
            }
            
            if (password.equals(user.getPassword())) {
                System.out.println("Authentication: Password match for user: " + username);
                return user;
            }
            
            System.out.println("Authentication: Password mismatch for user: " + username);
            return null;
        } catch (Exception e) {
            System.err.println("Authentication error: " + e.getMessage());
            return null;
        }
    }
    
    public List<User> findByCreatedBy(String managerId) {
        try {
            RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(TABLE_NAME);
            criteria.setMaxVersions(1);
            criteria.setLimit(100);
            criteria.setIndexName("createdBy_index");
            
            PrimaryKeyBuilder startKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            startKey.addPrimaryKeyColumn("createdBy", PrimaryKeyValue.fromString(managerId));
            criteria.setInclusiveStartPrimaryKey(startKey.build());
            
            PrimaryKeyBuilder endKey = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            endKey.addPrimaryKeyColumn("createdBy", PrimaryKeyValue.fromString(managerId));
            criteria.setExclusiveEndPrimaryKey(endKey.build());
            
            GetRangeResponse response = tablestoreClient.getRange(new GetRangeRequest(criteria));
            
            List<User> users = new ArrayList<>();
            for (Row row : response.getRows()) {
                users.add(rowToUser(row));
            }
            return users;
        } catch (Exception e) {
            System.err.println("Error finding users by createdBy: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public void delete(String userId) {
        try {
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(userId));
            
            RowDeleteChange rowDeleteChange = new RowDeleteChange(TABLE_NAME, primaryKeyBuilder.build());
            tablestoreClient.deleteRow(new DeleteRowRequest(rowDeleteChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete user", e);
        }
    }
    
    // Helper methods
    private User rowToUser(Row row) {
        try {
            Map<String, Object> dataMap = new HashMap<>();
            
            // Add primary key
            for (PrimaryKeyColumn column : row.getPrimaryKey().getPrimaryKeyColumns()) {
                dataMap.put(column.getName(), column.getValue().asString());
            }
            
            // Add columns
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
            
            return objectMapper.convertValue(dataMap, User.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert row to User", e);
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
            // For complex objects, serialize to JSON string
            try {
                String jsonValue = objectMapper.writeValueAsString(value);
                rowPutChange.addColumn(name, ColumnValue.fromString(jsonValue));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize column: " + name, e);
            }
        }
    }
    
    @Override
    public void addNotification(String userId, String notificationId, java.util.Map<String, Object> notification) {
        try {
            // Get user's current notifications
            User user = findById(userId);
            if (user == null) {
                throw new RuntimeException("User not found: " + userId);
            }
            
            // Add new notification to user's notifications map
            java.util.Map<String, Object> notifications = user.getNotifications();
            if (notifications == null) {
                notifications = new java.util.HashMap<>();
            }
            notifications.put(notificationId, notification);
            
            // Update user with new notifications
            PrimaryKeyBuilder primaryKeyBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
            primaryKeyBuilder.addPrimaryKeyColumn("id", PrimaryKeyValue.fromString(userId));
            
            RowUpdateChange rowUpdateChange = new RowUpdateChange(TABLE_NAME, primaryKeyBuilder.build());
            String notificationsJson = objectMapper.writeValueAsString(notifications);
            rowUpdateChange.put("notifications", ColumnValue.fromString(notificationsJson));
            
            tablestoreClient.updateRow(new UpdateRowRequest(rowUpdateChange));
        } catch (Exception e) {
            throw new RuntimeException("Failed to add notification", e);
        }
    }
}
