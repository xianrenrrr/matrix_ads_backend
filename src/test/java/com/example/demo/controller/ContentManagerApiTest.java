package com.example.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
@AutoConfigureMockMvc
public class ContentManagerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetSubmittedVideo() throws Exception {
        String compositeId = "user123_template456";
        
        mockMvc.perform(get("/content-manager/templates/submitted-videos/" + compositeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", is(compositeId)))
            .andExpect(jsonPath("$.data.templateId", is("template456")))
            .andExpect(jsonPath("$.data.uploadedBy", is("user123")))
            .andExpect(jsonPath("$.data.scenes", notNullValue()))
            .andExpect(jsonPath("$.data.scenes.1.sceneNumber", is(1)))
            .andExpect(jsonPath("$.data.scenes.1.status", is("approved")))
            .andExpect(jsonPath("$.data.scenes.2.sceneNumber", is(2)))
            .andExpect(jsonPath("$.data.scenes.2.status", is("pending")))
            .andExpect(jsonPath("$.data.progress", notNullValue()))
            .andExpect(jsonPath("$.data.progress.totalScenes", is(2)))
            .andExpect(jsonPath("$.data.progress.approved", is(1)))
            .andExpect(jsonPath("$.data.progress.pending", is(1)))
            .andExpect(jsonPath("$.data.progress.rejected", is(0)))
            .andExpect(jsonPath("$.data.progress.completionPercentage", is(50)));
    }

    @Test
    public void testGetSubmittedVideoNotFound() throws Exception {
        mockMvc.perform(get("/content-manager/templates/submitted-videos/nonexistent_video"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", containsString("not found")));
    }
}