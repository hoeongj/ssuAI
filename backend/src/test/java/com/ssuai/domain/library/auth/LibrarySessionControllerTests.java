package com.ssuai.domain.library.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(LibrarySessionController.class)
@Import({LibrarySessionStore.class, LibrarySessionProperties.class})
class LibrarySessionControllerTests {

    private final MockMvc mockMvc;
    private final LibrarySessionStore store;

    @Autowired
    LibrarySessionControllerTests(MockMvc mockMvc, LibrarySessionStore store) {
        this.mockMvc = mockMvc;
        this.store = store;
    }

    @Test
    void captureSessionStoresTokenAndReturns201() throws Exception {
        String body = "{\"token\":\"ssotoken-aaaaaaaaaaaaaa\"}";

        mockMvc.perform(post("/api/library/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.error").value(nullValue()));

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void rejectsBlankToken() throws Exception {
        mockMvc.perform(post("/api/library/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsTokenWithInvalidCharacters() throws Exception {
        mockMvc.perform(post("/api/library/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad token with spaces\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsTooShortToken() throws Exception {
        mockMvc.perform(post("/api/library/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
