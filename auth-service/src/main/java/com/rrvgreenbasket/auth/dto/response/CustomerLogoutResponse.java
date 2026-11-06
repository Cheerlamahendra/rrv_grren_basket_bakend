package com.rrvgreenbasket.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerLogoutResponse
 *
 * <p><strong>What it does:</strong></p>
 * Structured response payload confirming successful logout and session invalidation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLogoutResponse {

    private boolean loggedOut;
}
