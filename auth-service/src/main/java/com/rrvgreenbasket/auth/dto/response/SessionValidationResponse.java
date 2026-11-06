package com.rrvgreenbasket.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SessionValidationResponse
 *
 * <p><strong>What it does:</strong></p>
 * Payload returned by internal session validation endpoint to Gateway and microservices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionValidationResponse {

    private boolean valid;
    private String sessionId;
    private String userId; // Canonical AuthUser public UUID
    private String role;
    private String status;
}
