
package com.rrvgreenbasket.cart.dto.client;

import java.time.OffsetDateTime;

public record ServiceApiResponse<T>(
        Boolean success,
        String message,
        T data,
        OffsetDateTime timestamp
) {
}
