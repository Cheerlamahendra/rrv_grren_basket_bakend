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
                .publicId("b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e")
                .orderAcceptanceEnabled(true)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(22, 0))
                .timezone("Asia/Kolkata")
                .deliveryType(DeliveryType.NEXT_DAY)
                .deliveryDaysOffset(1)
                .version(3L)
                .build();
    }

    private OrderBusinessSettingsService createServiceWithFixedClock(LocalDateTime ldt, ZoneId zoneId) {
        Clock fixedClock = Clock.fixed(ldt.atZone(zoneId).toInstant(), zoneId);
        return new OrderBusinessSettingsServiceImpl(repository, fixedClock);
    }

    @Test
    @DisplayName("Should reject order before opening at 05:59 AM IST")
    void shouldRejectOrderAt0559() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 5, 59, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed")
                .hasMessageContaining("6:00 AM")
                .hasMessageContaining("10:00 PM");
    }

    @Test
    @DisplayName("Should accept order at exact opening time 06:00 AM IST (inclusive opening boundary)")
    void shouldAcceptOrderAt0600() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 6, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("Should accept order within business window at 12:00 PM IST (midday)")
    void shouldAcceptOrderAt1200() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 12, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("Should accept order within business window at 1:13 PM IST (13:13) - MUST BE OPEN")
    void shouldAcceptOrderAt1313() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 13, 13, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("Should accept order within business window at 21:59 IST (9:59 PM - just before closing)")
    void shouldAcceptOrderAt2159() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 21, 59, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("Should reject order at exact closing time 22:00 IST (10:00 PM - exclusive cutoff boundary)")
    void shouldRejectOrderAt2200() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 22, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed")
                .hasMessageContaining("6:00 AM")
                .hasMessageContaining("10:00 PM");
    }

    @Test
    @DisplayName("Should reject order after closing time at 22:01 IST (10:01 PM)")
    void shouldRejectOrderAt2201() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 22, 1, 0);
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

    @Test
    @DisplayName("Should reject update if opening time equals closing time")
    void shouldRejectEqualOpeningClosingTimes() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultSettings));

        OrderBusinessSettingsService service = createServiceWithFixedClock(
                LocalDateTime.now(), ZoneId.of("Asia/Kolkata")
        );

        UpdateOrderBusinessSettingsRequest invalidRequest = UpdateOrderBusinessSettingsRequest.builder()
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(10, 0))
                .orderAcceptanceEnabled(true)
                .timezone("Asia/Kolkata")
                .build();

        assertThatThrownBy(() -> service.updateSettings(invalidRequest, "admin"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("cannot be identical");
    }

    @Test
    @DisplayName("Should update closing time and dynamically affect order acceptance window")
    void shouldDynamicallyUpdateClosingTime() {
        OrderBusinessSettings dynamicSettings = OrderBusinessSettings.builder()
                .id(1L)
                .orderAcceptanceEnabled(true)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(22, 0))
                .timezone("Asia/Kolkata")
                .build();

        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(dynamicSettings));
        when(repository.save(any(OrderBusinessSettings.class))).thenAnswer(i -> i.getArgument(0));

        // 9:30 PM (21:30) - Allowed when closing is 22:00 (10:00 PM)
        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 21, 30, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));
        service.validateOrderAcceptanceWindow(); // Allowed

        // Admin updates closing time from 22:00 to 21:00 (9:00 PM)
        UpdateOrderBusinessSettingsRequest updateRequest = UpdateOrderBusinessSettingsRequest.builder()
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(21, 0))
                .timezone("Asia/Kolkata")
                .build();
        service.updateSettings(updateRequest, "admin");

        // Now 9:30 PM must be rejected immediately without restart
        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed");
    }
}
