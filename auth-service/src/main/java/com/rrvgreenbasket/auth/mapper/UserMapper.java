
package com.rrvgreenbasket.auth.mapper;

import com.rrvgreenbasket.auth.dto.response.UserSummaryResponse;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.enums.UserRole;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;

@Component
public class UserMapper {

    public UserSummaryResponse toSummaryResponse(AuthUser user) {
        if (user == null) {
            return null;
        }

        String email = null;

        if (user.getAdminAuth() != null) {
            email = user.getAdminAuth().getEmail();
        } else if (user.getCustomerAuth() != null) {
            email = user.getCustomerAuth().getEmail();
        }

        String mobileNumber = user.getCustomerAuth() != null
                ? user.getCustomerAuth().getMobileNumber()
                : null;

        String roleName;

        if (user.getRole() == UserRole.CUSTOMER
                || user.getRole() == UserRole.USER) {
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

    // New method: supports customer full name
    public UserSummaryResponse toSummaryResponse(
            AuthUser user,
            String fullName) {

        UserSummaryResponse response = toSummaryResponse(user);

        if (response != null) {
            response.setFullName(fullName);
        }

        return response;
    }
}
