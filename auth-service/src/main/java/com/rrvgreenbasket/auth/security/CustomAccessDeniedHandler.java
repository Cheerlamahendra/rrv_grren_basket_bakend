package com.rrvgreenbasket.auth.security;

import com.rrvgreenbasket.auth.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CustomAccessDeniedHandler
 *
 * <p><strong>What it does:</strong></p>
 * Invoked when an authenticated user attempts to access a protected resource without
 * having the required role authority (e.g., Customer attempting an Admin route),
 * writing a standard {@link ApiResponse} JSON response with HTTP 403 Forbidden.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApiResponse<Void> apiResponse = ApiResponse.error("Access Denied: Insufficient permissions to access this endpoint");
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
