package com.rrvgreenbasket.order.service.impl;

import com.rrvgreenbasket.order.dto.request.UpdateOrderBusinessSettingsRequest;
import com.rrvgreenbasket.order.dto.response.OrderBusinessSettingsResponse;
import com.rrvgreenbasket.order.entity.OrderBusinessSettings;
import com.rrvgreenbasket.order.enums.DeliveryType;
import com.rrvgreenbasket.order.exception.OrderCutoffException;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import com.rrvgreenbasket.order.repository.OrderBusinessSettingsRepository;
import com.rrvgreenbasket.order.service.OrderBusinessSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBusinessSettingsServiceImpl implements OrderBusinessSettingsService {

    private final OrderBusinessSettingsRepository settingsRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public OrderBusinessSettings getSettingsEntity() {
        return settingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::initDefaultSettings);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderBusinessSettingsResponse getSettings() {
        return mapToResponse(getSettingsEntity());
    }

    @Override
    @Transactional
    public OrderBusinessSettingsResponse updateSettings(UpdateOrderBusinessSettingsRequest request, String adminUserId) {
        log.info("Admin '{}' updating order business settings", adminUserId);

        if (request.getOpeningTime() == null || request.getClosingTime() == null) {
            throw new OrderValidationException("Opening time and closing time are required");
        }

        if (request.getOpeningTime().equals(request.getClosingTime())) {
            throw new OrderValidationException("Opening time and closing time cannot be identical");
        }

        if (!request.getClosingTime().isAfter(request.getOpeningTime())) {
            throw new OrderValidationException("Opening time (" + request.getOpeningTime() + ") must be earlier than closing time (" + request.getClosingTime() + ") on the same calendar day");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(request.getTimezone() != null && !request.getTimezone().isBlank()
                    ? request.getTimezone().trim() : "Asia/Kolkata");
        } catch (Exception e) {
            throw new OrderValidationException("Invalid timezone identifier: " + request.getTimezone());
        }

        OrderBusinessSettings settings = getSettingsEntity();
        if (request.getOrderAcceptanceEnabled() != null) {
            settings.setOrderAcceptanceEnabled(request.getOrderAcceptanceEnabled());
        }
        settings.setOpeningTime(request.getOpeningTime());
        settings.setClosingTime(request.getClosingTime());
        if (request.getDeliveryType() != null) {
            settings.setDeliveryType(request.getDeliveryType());
        }
        if (request.getDeliveryDaysOffset() != null) {
            settings.setDeliveryDaysOffset(request.getDeliveryDaysOffset());
        }
        settings.setTimezone(zoneId.getId());
        settings.setUpdatedBy(adminUserId);

        OrderBusinessSettings saved = settingsRepository.save(settings);
        log.info("Order business settings updated successfully: opening={}, closing={}, timezone={}",
                saved.getOpeningTime(), saved.getClosingTime(), saved.getTimezone());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public OrderBusinessSettingsResponse updateOrderAcceptanceStatus(boolean enabled, String adminUserId) {
        log.info("Admin '{}' updating order acceptance enabled status to: {}", adminUserId, enabled);
        OrderBusinessSettings settings = getSettingsEntity();
        settings.setOrderAcceptanceEnabled(enabled);
        settings.setUpdatedBy(adminUserId);
        OrderBusinessSettings saved = settingsRepository.save(settings);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateOrderAcceptanceWindow() {
        OrderBusinessSettings settings = getSettingsEntity();

        if (!Boolean.TRUE.equals(settings.getOrderAcceptanceEnabled())) {
            log.warn("Order placement rejected: order acceptance is globally disabled by admin");
            throw new OrderCutoffException(
                    "Order placement is currently disabled by administrator.",
                    false,
                    settings.getOpeningTime(),
                    settings.getClosingTime(),
                    settings.getOpeningTime()
            );
        }

        ZonedDateTime now = getCurrentBusinessDateTime();
        LocalTime currentTime = now.toLocalTime();
        LocalTime opening = settings.getOpeningTime();
        LocalTime closing = settings.getClosingTime();

        log.debug("Checking order window: current={}, opening={}, closing={}", currentTime, opening, closing);

        // Opening time is inclusive, closing time is exclusive: opening <= currentTime < closing
        if (currentTime.isBefore(opening) || !currentTime.isBefore(closing)) {
            String msg = String.format("Order placement is currently closed. Orders are accepted between %s and %s (%s). Current time is %s.",
                    formatTime(opening), formatTime(closing), settings.getTimezone(), formatTime(currentTime));
            log.warn("Order placement rejected outside operating window: {}", msg);

            throw new OrderCutoffException(
                    msg,
                    true,
                    opening,
                    closing,
                    opening
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDate calculatePromisedDeliveryDate(LocalDate orderDate) {
        OrderBusinessSettings settings = getSettingsEntity();
        int offset = settings.getDeliveryDaysOffset() != null ? settings.getDeliveryDaysOffset() : 1;
        return orderDate.plusDays(offset);
    }

    @Override
    public ZonedDateTime getCurrentBusinessDateTime() {
        OrderBusinessSettings settings = getSettingsEntity();
        ZoneId zoneId = ZoneId.of(settings.getTimezone() != null ? settings.getTimezone() : "Asia/Kolkata");
        return ZonedDateTime.now(clock.withZone(zoneId));
    }

    private OrderBusinessSettings initDefaultSettings() {
        OrderBusinessSettings settings = OrderBusinessSettings.builder()
                .publicId(UUID.randomUUID().toString())
                .orderAcceptanceEnabled(true)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(22, 0))
                .deliveryType(DeliveryType.NEXT_DAY)
                .deliveryDaysOffset(1)
                .timezone("Asia/Kolkata")
                .updatedBy("SYSTEM")
                .build();
        return settingsRepository.save(settings);
    }

    private String formatTime(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour >= 12 ? "PM" : "AM";
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        return minute == 0 ? String.format("%d:00 %s", displayHour, period)
                : String.format("%d:%02d %s", displayHour, minute, period);
    }

    private OrderBusinessSettingsResponse mapToResponse(OrderBusinessSettings settings) {
        return OrderBusinessSettingsResponse.builder()
                .publicId(settings.getPublicId())
                .orderAcceptanceEnabled(Boolean.TRUE.equals(settings.getOrderAcceptanceEnabled()))
                .openingTime(settings.getOpeningTime())
                .closingTime(settings.getClosingTime())
                .deliveryType(settings.getDeliveryType())
                .deliveryDaysOffset(settings.getDeliveryDaysOffset())
                .timezone(settings.getTimezone())
                .updatedAt(settings.getUpdatedAt())
                .updatedBy(settings.getUpdatedBy())
                .build();
    }
}
