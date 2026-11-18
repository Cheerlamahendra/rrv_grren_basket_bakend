package com.rrvgreenbasket.order.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderWindowClosedResponse {

    private boolean orderAcceptanceEnabled;
    private String openingTime;
    private String closingTime;
    private String nextOpeningTime;
}
