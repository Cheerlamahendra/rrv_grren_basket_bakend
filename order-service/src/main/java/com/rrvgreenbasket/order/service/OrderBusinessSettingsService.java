package com.rrvgreenbasket.order.service;

import com.rrvgreenbasket.order.dto.request.UpdateOrderBusinessSettingsRequest;
import com.rrvgreenbasket.order.dto.response.OrderBusinessSettingsResponse;
import com.rrvgreenbasket.order.entity.OrderBusinessSettings;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public interface OrderBusinessSettingsService {

    OrderBusinessSettings getSettingsEntity();

    OrderBusinessSettingsResponse getSettings();

    OrderBusinessSettingsResponse updateSettings(UpdateOrderBusinessSettingsRequest request, String adminUserId);

    OrderBusinessSettingsResponse updateOrderAcceptanceStatus(boolean enabled, String adminUserId);

    void validateOrderAcceptanceWindow();

    LocalDate calculatePromisedDeliveryDate(LocalDate orderDate);

    ZonedDateTime getCurrentBusinessDateTime();
}
