package com.rrvgreenbasket.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceApiResponse<T>(
        Boolean success,
        String message,
        T data,
        String timestamp
) {}
