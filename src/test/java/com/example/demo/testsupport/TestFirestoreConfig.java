package com.example.demo.testsupport;

import com.google.cloud.firestore.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration that provides a no-op Firestore implementation for testing.
 * This avoids the need for Mockito which has issues with Java 24.
 */
@Configuration
@Profile("ci")
public class TestFirestoreConfig {

    @Bean
    @Primary
    public Firestore firestore() {
        // Create a simple proxy implementation that returns safe defaults
        return (Firestore) Proxy.newProxyInstance(
            Firestore.class.getClassLoader(),
            new Class[] { Firestore.class },
            new FirestoreInvocationHandler()
        );
    }

    private static class FirestoreInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            // Handle common Firestore methods with safe defaults
            if ("collection".equals(methodName)) {
                return createMockCollectionReference();
            }
            
            if ("close".equals(methodName)) {
                return null;
            }
            
            // Return null for other methods
            return null;
        }
        
        private CollectionReference createMockCollectionReference() {
            return (CollectionReference) Proxy.newProxyInstance(
                CollectionReference.class.getClassLoader(),
                new Class[] { CollectionReference.class },
                new CollectionReferenceHandler()
            );
        }
    }
    
    private static class CollectionReferenceHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            if ("document".equals(methodName)) {
                return createMockDocumentReference();
            }
            
            if ("get".equals(methodName)) {
                return createMockQueryFuture();
            }
            
            return null;
        }
        
        private DocumentReference createMockDocumentReference() {
            return (DocumentReference) Proxy.newProxyInstance(
                DocumentReference.class.getClassLoader(),
                new Class[] { DocumentReference.class },
                new DocumentReferenceHandler()
            );
        }
        
        private com.google.api.core.ApiFuture<QuerySnapshot> createMockQueryFuture() {
            QuerySnapshot snapshot = (QuerySnapshot) Proxy.newProxyInstance(
                QuerySnapshot.class.getClassLoader(),
                new Class[] { QuerySnapshot.class },
                (proxy, method, args) -> {
                    if ("isEmpty".equals(method.getName())) {
                        return true;
                    }
                    return null;
                }
            );
            return com.google.api.core.ApiFutures.immediateFuture(snapshot);
        }
    }
    
    private static class DocumentReferenceHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            if ("get".equals(methodName)) {
                return createMockDocumentSnapshotFuture();
            }
            
            if ("set".equals(methodName) || "update".equals(methodName) || "delete".equals(methodName)) {
                return com.google.api.core.ApiFutures.immediateFuture(null);
            }
            
            return null;
        }
        
        private com.google.api.core.ApiFuture<DocumentSnapshot> createMockDocumentSnapshotFuture() {
            DocumentSnapshot snapshot = (DocumentSnapshot) Proxy.newProxyInstance(
                DocumentSnapshot.class.getClassLoader(),
                new Class[] { DocumentSnapshot.class },
                new DocumentSnapshotHandler()
            );
            return com.google.api.core.ApiFutures.immediateFuture(snapshot);
        }
    }
    
    private static class DocumentSnapshotHandler implements InvocationHandler {
        private final Map<String, Object> testData;
        
        public DocumentSnapshotHandler() {
            this.testData = new HashMap<>();
            testData.put("id", "user123_template456");
            testData.put("templateId", "template456");
            testData.put("uploadedBy", "user123");
            testData.put("publishStatus", "pending");
            
            Map<String, Object> scenes = new HashMap<>();
            Map<String, Object> scene1 = new HashMap<>();
            scene1.put("sceneNumber", 1);
            scene1.put("status", "approved");
            scenes.put("1", scene1);
            
            Map<String, Object> scene2 = new HashMap<>();
            scene2.put("sceneNumber", 2);
            scene2.put("status", "pending");
            scenes.put("2", scene2);
            
            testData.put("scenes", scenes);
            
            Map<String, Object> progress = new HashMap<>();
            progress.put("totalScenes", 2);
            progress.put("approved", 1);
            progress.put("pending", 1);
            progress.put("rejected", 0);
            progress.put("completionPercentage", 50);
            testData.put("progress", progress);
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            if ("exists".equals(methodName)) {
                return true;
            }
            
            if ("getData".equals(methodName)) {
                return testData;
            }
            
            if ("get".equals(methodName) && args != null && args.length > 0) {
                return testData.get(args[0]);
            }
            
            if ("getId".equals(methodName)) {
                return "user123_template456";
            }
            
            if ("contains".equals(methodName)) {
                return args != null && args.length > 0 && testData.containsKey(args[0]);
            }
            
            return null;
        }
    }
}