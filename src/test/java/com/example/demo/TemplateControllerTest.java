package com.example.demo;

import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TemplateControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCreateTemplate() throws Exception {
        // Build a ManualTemplate object
        ManualTemplate template = new ManualTemplate();
        template.setUserId("user123");
        template.setTemplateTitle("New Product Launch Campaign");
        template.setTotalVideoLength(30);
        template.setVideoPurpose("Product promotion for young shoppers");
        template.setTone("Friendly");
        template.setVideoFormat("1080p 9:16");
        template.setLightingRequirements("Natural daylight preferred");
        template.setBackgroundMusic("Calm instrumental music");

        Scene scene = new Scene();
        scene.setSceneNumber(1);
        scene.setSceneTitle("Welcome Message");
        scene.setSceneDuration(10);
        scene.setScriptLine("Welcome to our new product launch!");
        scene.setPresenceOfPerson(true);
        scene.setPreferredGender("No Preference");
        scene.setPersonPosition("Center");
        scene.setDeviceOrientation("Phone");
        scene.setScreenGridOverlayLabels(Arrays.asList("Human", "Product", "Logo"));
        scene.setBackgroundInstructions("Store interior, bright lighting");
        scene.setSpecificCameraInstructions("Camera chest height");
        scene.setMovementInstructions("No Movement");
        scene.setAudioNotes("Capture natural sound");
        scene.setOtherNotes("N/A");
        template.setScenes(Arrays.asList(scene));

        // Wrap in CreateTemplateRequest
        String requestJson = "{" +
                "\"userId\": \"user123\"," +
                "\"manualTemplate\": " + objectMapper.writeValueAsString(template) +
                "}";

        mockMvc.perform(post("/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());
    }
}
