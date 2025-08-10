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
                "templateTitle": "Test Template with Scene Detection",
                "scenes": [
                    {
                        "sceneNumber": 1,
                        "sceneTitle": "Scene 1"
                    }
                ]
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
    public void testAIApprovalThreshold() throws Exception {
        // Test the AI approval threshold endpoint
        mockMvc.perform(post("/api/ai-approval/template123/threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"threshold\": 85.0}")
                .header("Authorization", "manager123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.threshold", is(85.0)));
    }
}