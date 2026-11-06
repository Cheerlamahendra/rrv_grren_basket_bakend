package com.rrvgreenbasket.auth.dto.response;

import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * UserSummaryResponse
 *
 * <p><strong>What it does:</strong></p>
 * Non-sensitive user summary model returned upon successful authentication or token refresh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {

    private String publicId;
    private String email;
    private String mobileNumber;
    private UserRole role;
    private UserStatus status;
    private Set<String> roles;
}
