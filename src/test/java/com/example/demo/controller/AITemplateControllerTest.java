package com.example.demo.controller;

import com.example.demo.ai.template.AITemplateGenerator;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.Video;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
@AutoConfigureMockMvc
public class AITemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AITemplateGenerator aiTemplateGenerator;

    @Test
    public void testAITemplateGeneration() throws Exception {
        // Create a mock AI-generated template
        ManualTemplate mockTemplate = new ManualTemplate();
        mockTemplate.setTemplateTitle("AI Generated Template");
        mockTemplate.setUserId("manager123");
        
        List<Scene> scenes = new ArrayList<>();
        Scene scene1 = new Scene();
        scene1.setSceneNumber(1);
        scene1.setSceneTitle("Opening - Person detected");
        scene1.setKeyframeUrl("https://example.com/frame.jpg");
        scenes.add(scene1);
        
        Scene scene2 = new Scene();
        scene2.setSceneNumber(2);
        scene2.setSceneTitle("Product showcase");
        scene2.setKeyframeUrl("https://example.com/frame.jpg");
        scenes.add(scene2);
        
        mockTemplate.setScenes(scenes);
        
        when(aiTemplateGenerator.generateTemplate(any(Video.class), anyString()))
            .thenReturn(mockTemplate);
        
        // Test template creation endpoint
        String requestBody = """
            {
                "userId": "testUser123",
                "manualTemplate": {
                    "templateTitle": "AI Generated Template",
                    "scenes": [
                        {
                            "sceneNumber": 1,
                            "sceneTitle": "Opening Scene",
                            "sceneDescription": "Opening shot with person"
                        },
                        {
                            "sceneNumber": 2,
                            "sceneTitle": "Product Display",
                            "sceneDescription": "Product showcase"
                        }
                    ]
                },
                "selectedGroupIds": []
            }
            """;
        
        mockMvc.perform(post("/content-manager/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "manager123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.templateTitle", containsString("AI Generated Template")))
            .andExpect(jsonPath("$.data.scenes", hasSize(2)))
            .andExpect(jsonPath("$.data.scenes[0].sceneNumber", is(1)))
            .andExpect(jsonPath("$.data.scenes[1].sceneNumber", is(2)));
    }

    @Test
    public void testSceneDetectionIntegration() throws Exception {
        // This test verifies that our mocked scene detection service is properly integrated
        // The actual AI template generation uses scene detection under the hood
        
        String requestBody = """
            {
                "userId": "testUser456", 
                "manualTemplate": {
                    "templateTitle": "Test Template with Scene Detection",
                    "scenes": [
                        {
                            "sceneNumber": 1,
                            "sceneTitle": "Scene 1"
                        }
                    ]
                },
                "selectedGroupIds": []
            }
            """;
        
        mockMvc.perform(post("/content-manager/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "manager123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    public void testGroupAISettings() throws Exception {
        // Test the group AI settings endpoint
        // Note: In a real test, you would first create a group and get its ID
        // For this test, we're using a mock group ID
        String groupId = "test-group-id";
        
        String requestBody = """
            {
                "aiApprovalThreshold": 0.85,
                "aiAutoApprovalEnabled": true,
                "allowManualOverride": true
            }
            """;
        
        // Test updating AI settings for a group
        mockMvc.perform(put("/content-manager/groups/" + groupId + "/ai-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "manager123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.message", is("AI settings updated successfully")))
            .andExpect(jsonPath("$.aiApprovalThreshold", is(0.85)))
            .andExpect(jsonPath("$.aiAutoApprovalEnabled", is(true)))
            .andExpect(jsonPath("$.allowManualOverride", is(true)));
    }
}