package com.rrvgreenbasket.order.service;

import com.rrvgreenbasket.order.dto.request.UpdateOrderBusinessSettingsRequest;
import com.rrvgreenbasket.order.dto.response.OrderBusinessSettingsResponse;
import com.rrvgreenbasket.order.entity.OrderBusinessSettings;
import com.rrvgreenbasket.order.enums.DeliveryType;
import com.rrvgreenbasket.order.exception.OrderCutoffException;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import com.rrvgreenbasket.order.repository.OrderBusinessSettingsRepository;
import com.rrvgreenbasket.order.service.impl.OrderBusinessSettingsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderBusinessSettingsServiceTests {

    @Mock
    private OrderBusinessSettingsRepository repository;

    private OrderBusinessSettings defaultSettings;

    @BeforeEach
    void setUp() {
        defaultSettings = OrderBusinessSettings.builder()
                .id(1L)
                .orderAcceptanceEnabled(true)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(21, 0))
                .timezone("Asia/Kolkata")
                .deliveryType(DeliveryType.NEXT_DAY)
                .deliveryDaysOffset(1)
                .build();
    }

    private OrderBusinessSettingsService createServiceWithFixedClock(LocalDateTime ldt, ZoneId zoneId) {
        Clock fixedClock = Clock.fixed(ldt.atZone(zoneId).toInstant(), zoneId);
        return new OrderBusinessSettingsServiceImpl(repository, fixedClock);
    }

    @Test
    @DisplayName("Should accept order within business window at 8:00 AM (inclusive opening)")
    void shouldAcceptOrderAtOpeningTime() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        // 8:00 AM IST
        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 8, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("Should accept order within business window at 12:00 PM")
    void shouldAcceptOrderAtMidday() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 12, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow();
    }

    @Test
    @DisplayName("Should accept order within business window at 8:59:59 PM")
    void shouldAcceptOrderJustBeforeClosing() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 20, 59, 59);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow();
    }

    @Test
    @DisplayName("Should reject order before opening at 7:59:59 AM")
    void shouldRejectOrderBeforeOpeningTime() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 7, 59, 59);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed");
    }

    @Test
    @DisplayName("Should reject order at exact closing time 9:00 PM (exclusive cutoff)")
    void shouldRejectOrderAtExactClosingTime() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 21, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed");
    }

    @Test
    @DisplayName("Should reject order after closing time at 9:30 PM")
    void shouldRejectOrderAfterClosingTime() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 21, 30, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed");
    }

    @Test
    @DisplayName("Should reject order when order acceptance is explicitly disabled by admin")
    void shouldRejectOrderWhenAcceptanceDisabled() {
        defaultSettings.setOrderAcceptanceEnabled(false);
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        // Even at 12:00 PM
        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 12, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently disabled");
    }

    @Test
    @DisplayName("Should calculate promised delivery date with NEXT_DAY offset 1")
    void shouldCalculatePromisedDeliveryDate() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 10, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        LocalDate orderDate = LocalDate.of(2026, 11, 10); // Tuesday
        LocalDate deliveryDate = service.calculatePromisedDeliveryDate(orderDate);

        assertThat(deliveryDate).isEqualTo(LocalDate.of(2026, 11, 11)); // Wednesday
    }

    @Test
    @DisplayName("Should reject update if opening time is after closing time")
    void shouldRejectInvalidOpeningClosingTimes() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        OrderBusinessSettingsService service = createServiceWithFixedClock(
                LocalDateTime.now(), ZoneId.of("Asia/Kolkata")
        );

        UpdateOrderBusinessSettingsRequest invalidRequest = UpdateOrderBusinessSettingsRequest.builder()
                .openingTime(LocalTime.of(21, 0))
                .closingTime(LocalTime.of(8, 0))
                .deliveryDaysOffset(1)
                .deliveryType(DeliveryType.NEXT_DAY)
                .orderAcceptanceEnabled(true)
                .timezone("Asia/Kolkata")
                .build();

        assertThatThrownBy(() -> service.updateSettings(invalidRequest, "admin"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("must be earlier than closing time");
    }

    @Test
    @DisplayName("Should toggle order acceptance status")
    void shouldToggleOrderAcceptanceStatus() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));
        when(repository.save(any(OrderBusinessSettings.class))).thenAnswer(i -> i.getArgument(0));

        OrderBusinessSettingsService service = createServiceWithFixedClock(
                LocalDateTime.now(), ZoneId.of("Asia/Kolkata")
        );

        OrderBusinessSettingsResponse response = service.updateOrderAcceptanceStatus(false, "admin_user");
        assertThat(response.isOrderAcceptanceEnabled()).isFalse();
        verify(repository).save(any(OrderBusinessSettings.class));
    }
}
