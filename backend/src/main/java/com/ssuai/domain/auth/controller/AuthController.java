package com.ssuai.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssuai.domain.auth.dto.MeResponse;
import com.ssuai.domain.user.entity.Student;
import com.ssuai.domain.user.service.StudentService;
import com.ssuai.global.auth.AuthAttributes;
import com.ssuai.global.exception.UnauthorizedException;
import com.ssuai.global.response.ApiResponse;

/**
 * Authenticated endpoints for the current ssuAI user. Reads the student
 * identity off the request attributes populated by
 * {@code JwtAuthFilter} — Spring Security is intentionally not in play
 * (Task 14 spec §6).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StudentService studentService;

    public AuthController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(HttpServletRequest request) {
        Object studentId = request.getAttribute(AuthAttributes.STUDENT_ID);
        if (!(studentId instanceof String id) || id.isBlank()) {
            throw new UnauthorizedException();
        }
        Student student = studentService.findById(id)
                .orElseThrow(UnauthorizedException::new);
        return ApiResponse.success(MeResponse.from(student));
    }
}
