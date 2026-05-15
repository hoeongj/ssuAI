package com.ssuai.domain.library.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ssuai.domain.library.auth.dto.LibrarySessionCaptureRequest;
import com.ssuai.global.response.ApiResponse;

@RestController
@RequestMapping("/api/library/session")
@Tag(name = "Library", description = "Library upstream session capture")
public class LibrarySessionController {

    private static final Logger log = LoggerFactory.getLogger(LibrarySessionController.class);

    private final LibrarySessionStore sessionStore;

    public LibrarySessionController(LibrarySessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Capture the oasis.ssu.ac.kr ssotoken cookie for the current ssuAI session")
    public ApiResponse<Void> captureSession(
            @Valid @RequestBody LibrarySessionCaptureRequest request,
            HttpServletRequest httpRequest
    ) {
        String sessionKey = httpRequest.getSession().getId();
        sessionStore.put(sessionKey, request.token());
        log.info("library session captured: sessionKey={} tokenFingerprint={}",
                LibrarySessionStore.fingerprint(sessionKey),
                LibrarySessionStore.fingerprint(request.token()));
        return ApiResponse.success(null);
    }
}
