package com.ssuai.global.exception;

/**
 * Thrown when the saint.ssu.ac.kr phase 2 portal fetch fails or returns HTML
 * that {@link com.ssuai.domain.auth.saint.SaintSsoService#parseIdentity} cannot
 * map onto a student identity — upstream 5xx, missing
 * {@code .main_title} greeting span, missing
 * {@code <ul class="main_box09_con">} rows, missing 학번 row, or any other
 * shape change in the live portal. Like {@link SaintAuthFailedException},
 * the SSO callback controller maps this to a 302 redirect with
 * {@code error=portal_unavailable} instead of a JSON error body.
 */
public class SaintPortalUnavailableException extends RuntimeException {

    public SaintPortalUnavailableException(String message) {
        super(message);
    }

    public SaintPortalUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
