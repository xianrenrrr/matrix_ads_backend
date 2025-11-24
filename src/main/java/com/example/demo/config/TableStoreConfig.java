package com.example.demo.config;

import com.alicloud.openservices.tablestore.SyncClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TableStoreConfig {
    
    @Value("${alibaba.tablestore.endpoint:}")
    private String endpoint;
    
    @Value("${alibaba.tablestore.instance:}")
    private String instanceName;
    
    @Value("${alibaba.tablestore.access-key-id:}")
    private String accessKeyId;
    
    @Value("${alibaba.tablestore.access-key-secret:}")
    private String accessKeySecret;
    
    @Value("${database.provider:tablestore}")
    private String databaseProvider;
    
    @Bean
    public SyncClient tablestoreClient() {
        if (!"tablestore".equalsIgnoreCase(databaseProvider)) {
            System.out.println("TableStore disabled - database provider: " + databaseProvider);
            return null;
        }
        
        System.out.println("=== Initializing Alibaba TableStore ===");
        System.out.println("Endpoint: " + endpoint);
        System.out.println("Instance: " + instanceName);
        
        SyncClient client = new SyncClient(
            endpoint,
            accessKeyId,
            accessKeySecret,
            instanceName
        );
        
        System.out.println("✅ TableStore initialized successfully!");
        return client;
    }
}
