package com.rrvgreenbasket.customer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.customer.dto.response.ApiResponse;
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
 * Invoked when an authenticated user attempts to access a resource for which
 * their granted authorities are insufficient.
 * Returns an HTTP 403 Forbidden structured JSON response.
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

        ApiResponse<Void> apiResponse = ApiResponse.error("Forbidden: You do not possess the required privileges to access this resource");
        response.getOutputStream().println(objectMapper.writeValueAsString(apiResponse));
    }
}
