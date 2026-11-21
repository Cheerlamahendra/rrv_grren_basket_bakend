package com.rrvgreenbasket.auth.dto.response;

import lombok.Data;

@Data
public class CustomerProfileDto {
    private String id;
    private String authUserId;
    private String fullName;
    private String email;
    private String mobileNumber;
}