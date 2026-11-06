package com.rrvgreenbasket.auth.controller;

import com.rrvgreenbasket.auth.dto.response.SessionValidationResponse;
import com.rrvgreenbasket.auth.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * InternalSessionController
 *
 * <p><strong>What it does:</strong></p>
 * Microservice-internal endpoint exposing authoritative real-time session validation
 * to API Gateway and downstream services.
 * Protected by <code>InternalServiceSecurityFilter</code> via canonical HMAC-SHA256 signatures and nonces.
 */
@RestController
@RequestMapping("/api/auth/internal")
@Tag(name = "Internal S2S Auth", description = "Internal microservice session validation endpoints")
public class InternalSessionController {

    private static final Logger log = LoggerFactory.getLogger(InternalSessionController.class);

    private final SessionService sessionService;

    public InternalSessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "Validate Session Health (Internal S2S)", description = "Returns real-time session active status, user publicId, and role.")
    @GetMapping("/sessions/{sessionId}/validate")
    public ResponseEntity<SessionValidationResponse> validateSession(@PathVariable("sessionId") String sessionId) {
        log.debug("Internal S2S session validation request for sessionId='{}'", sessionId);
        SessionValidationResponse response = sessionService.validateSession(sessionId);
        return ResponseEntity.ok(response);
    }
}
