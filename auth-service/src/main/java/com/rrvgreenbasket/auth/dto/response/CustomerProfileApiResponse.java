package com.rrvgreenbasket.auth.dto.response;

import lombok.Data;

@Data
public class CustomerProfileApiResponse {
    private boolean success;
    private String message;
    private CustomerProfileDto data;
}