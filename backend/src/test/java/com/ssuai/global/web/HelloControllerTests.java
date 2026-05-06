package com.ssuai.global.web;

import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(HelloController.class)
class HelloControllerTests {

    private final MockMvc mockMvc;

    @Autowired
    HelloControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void helloReturnsSuccessEnvelope() throws Exception {
        mockMvc.perform(get("/api/hello").param("name", "World"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greeting").value("Hello, World"))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.traceId").value(not(isEmptyOrNullString())));
    }

    @Test
    void helloReturnsValidationErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/hello").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value(not(isEmptyOrNullString())));
    }
}
