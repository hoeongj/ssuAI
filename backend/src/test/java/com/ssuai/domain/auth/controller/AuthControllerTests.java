package com.ssuai.domain.auth.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ssuai.domain.user.entity.Student;
import com.ssuai.domain.user.service.StudentService;
import com.ssuai.global.auth.AuthAttributes;

@ActiveProfiles("test")
@WebMvcTest(AuthController.class)
class AuthControllerTests {

    private final MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @Autowired
    AuthControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void meReturnsCurrentStudentWhenAttributePresent() throws Exception {
        Student student = new Student("20231234", "홍길동", "컴퓨터학부", "재학", Instant.now());
        when(studentService.findById("20231234")).thenReturn(Optional.of(student));

        mockMvc.perform(get("/api/auth/me")
                        .requestAttr(AuthAttributes.STUDENT_ID, "20231234")
                        .requestAttr(AuthAttributes.STUDENT_NAME, "홍길동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("20231234"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.major").value("컴퓨터학부"))
                .andExpect(jsonPath("$.data.enrollmentStatus").value("재학"))
                .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    void meReturns401WhenAttributeMissing() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(studentService);
    }

    @Test
    void meReturns401WhenAttributeIsBlank() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .requestAttr(AuthAttributes.STUDENT_ID, "  "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(studentService);
    }

    @Test
    void meReturns401WhenStudentRowMissing() throws Exception {
        when(studentService.findById(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me")
                        .requestAttr(AuthAttributes.STUDENT_ID, "20239999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
