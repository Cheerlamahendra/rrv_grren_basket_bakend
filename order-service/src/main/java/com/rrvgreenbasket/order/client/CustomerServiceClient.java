package com.rrvgreenbasket.order.client;

import com.rrvgreenbasket.order.client.dto.AddressClientResponse;
import com.rrvgreenbasket.order.client.dto.CustomerClientResponse;
import com.rrvgreenbasket.order.dto.request.AddressDto;

public interface CustomerServiceClient {

    CustomerClientResponse getCustomerProfile(String customerIdentifier);

    AddressClientResponse getAddress(String customerIdentifier, String addressPublicId);

    AddressClientResponse saveAddress(String customerIdentifier, AddressDto addressDto);
}
