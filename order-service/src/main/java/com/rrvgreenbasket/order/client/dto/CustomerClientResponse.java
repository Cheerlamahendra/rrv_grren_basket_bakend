package com.rrvgreenbasket.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerClientResponse {
    private String id;
    private String authUserId;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String status;
    private List<AddressClientResponse> addresses;
}
