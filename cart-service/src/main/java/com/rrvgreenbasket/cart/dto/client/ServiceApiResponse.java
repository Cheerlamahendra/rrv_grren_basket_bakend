package com.rrvgreenbasket.cart.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceApiResponse<T>(
        Boolean success,
        String message,
        T data,
        Object timestamp
) {
}
