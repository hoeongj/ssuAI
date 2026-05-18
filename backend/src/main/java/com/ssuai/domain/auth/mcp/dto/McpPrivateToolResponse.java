package com.ssuai.domain.auth.mcp.dto;

import java.time.Instant;

/**
 * Wrapper returned by all private MCP tools (get_my_schedule, get_my_grades,
 * get_my_assignments, get_my_library_loans).
 *
 * <p>status=OK: {@code data} holds the payload; all other fields except mcpSessionId are null.
 * status=AUTH_REQUIRED: {@code loginUrl} and {@code provider} indicate what to do next;
 * {@code data} is null. The client should open {@code loginUrl} in a browser, then retry
 * the original private tool call with the same {@code mcpSessionId}.
 *
 * <p>Security: principalKey / studentId are never included in this response.
 */
public record McpPrivateToolResponse<T>(
        String status,
        String provider,
        String mcpSessionId,
        String loginUrl,
        Instant expiresAt,
        String message,
        T data) {

    public static <T> McpPrivateToolResponse<T> ok(String mcpSessionId, T data) {
        return new McpPrivateToolResponse<>("OK", null, mcpSessionId, null, null, null, data);
    }

    public static <T> McpPrivateToolResponse<T> authRequired(
            String mcpSessionId, String provider, String loginUrl, Instant expiresAt) {
        return new McpPrivateToolResponse<>(
                "AUTH_REQUIRED", provider, mcpSessionId, loginUrl, expiresAt,
                "Authentication required. Open loginUrl in a browser, then retry with the same mcpSessionId.",
                null);
    }
}
