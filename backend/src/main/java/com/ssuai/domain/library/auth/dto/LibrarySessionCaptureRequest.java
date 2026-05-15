package com.ssuai.domain.library.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/library/session. Captures the raw `ssotoken` cookie
 * value the frontend read from oasis.ssu.ac.kr after the user logged in.
 *
 * Constraints intentionally tight — anything pasted here goes verbatim
 * into the Cookie header of upstream Pyxis calls, so we shape-validate
 * before storing.
 */
public record LibrarySessionCaptureRequest(
        @NotBlank
        @Size(min = 8, max = 4096)
        @Pattern(regexp = "^[A-Za-z0-9._\\-+/=]+$",
                message = "must match an opaque cookie value")
        String token
) {
}
