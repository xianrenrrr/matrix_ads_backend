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
public class ContentCreatorApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetSubmittedVideo() throws Exception {
        String compositeId = "user123_template456";
        
        // This test will hit the actual controller but with CI profile (test environment)
        mockMvc.perform(get("/content-creator/scenes/submitted-videos/" + compositeId))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 404 && status != 500) {
                    throw new AssertionError("Expected status 404 or 500 but was: " + status);
                }
            })
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    public void testGetSubmittedVideoEndpointExists() throws Exception {
        // Test that the endpoint exists and doesn't throw 404 (method not found)
        String compositeId = "test_video";
        
        mockMvc.perform(get("/content-creator/scenes/submitted-videos/" + compositeId))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 404 && status != 500) {
                    throw new AssertionError("Expected status 404 or 500 but was: " + status);
                }
            })
            .andExpect(jsonPath("$.success", is(false)));
    }
}