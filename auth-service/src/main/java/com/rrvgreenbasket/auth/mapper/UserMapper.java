package com.rrvgreenbasket.auth.mapper;

import com.rrvgreenbasket.auth.dto.response.UserSummaryResponse;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.enums.UserRole;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;

/**
 * UserMapper
 *
 * <p><strong>What it does:</strong></p>
 * Converts {@link AuthUser} composite entities into sanitized {@link UserSummaryResponse} DTOs.
 */
@Component
public class UserMapper {

    public UserSummaryResponse toSummaryResponse(AuthUser user) {
        if (user == null) {
            return null;
        }

        String email = user.getAdminAuth() != null ? user.getAdminAuth().getEmail() : null;
        String mobileNumber = user.getCustomerAuth() != null ? user.getCustomerAuth().getMobileNumber() : null;

        String roleName;
        if (user.getRole() == UserRole.CUSTOMER || user.getRole() == UserRole.USER) {
            roleName = "ROLE_CUSTOMER";
        } else if (user.getRole() != null) {
            roleName = "ROLE_" + user.getRole().name();
        } else {
            roleName = "ROLE_CUSTOMER";
        }

        return UserSummaryResponse.builder()
                .publicId(user.getPublicId())
                .email(email)
                .mobileNumber(mobileNumber)
                .role(user.getRole())
                .status(user.getStatus())
                .roles(new HashSet<>(Collections.singletonList(roleName)))
                .build();
    }
}
