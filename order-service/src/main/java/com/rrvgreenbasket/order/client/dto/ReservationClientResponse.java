package com.rrvgreenbasket.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReservationClientResponse {

    private String publicId;
    private String orderPublicId;
    private String status;
}
