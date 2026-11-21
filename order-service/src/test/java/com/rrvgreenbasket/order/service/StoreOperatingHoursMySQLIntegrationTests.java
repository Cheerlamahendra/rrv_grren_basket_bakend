package com.rrvgreenbasket.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.dto.response.OrderBusinessSettingsResponse;
import com.rrvgreenbasket.order.entity.OrderBusinessSettings;
import com.rrvgreenbasket.order.enums.DeliveryType;
import com.rrvgreenbasket.order.exception.OrderCutoffException;
import com.rrvgreenbasket.order.repository.OrderBusinessSettingsRepository;
import com.rrvgreenbasket.order.service.impl.OrderBusinessSettingsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true",
    "spring.datasource.username=root",
    "spring.datasource.password=mahendra",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
    "spring.flyway.enabled=false"
})
@DisplayName("Store Operating Hours MySQL Integration Tests")
public class StoreOperatingHoursMySQLIntegrationTests {

    @Autowired
    private OrderBusinessSettingsRepository repository;

    private OrderBusinessSettingsService createServiceWithFixedClock(LocalDateTime ldt, ZoneId zoneId) {
        Clock fixedClock = Clock.fixed(ldt.atZone(zoneId).toInstant(), zoneId);
        return new OrderBusinessSettingsServiceImpl(repository, fixedClock);
    }

    @Test
    @DisplayName("Verify actual MySQL record: opening=06:00, closing=22:00, timezone=Asia/Kolkata, enabled=true")
    void testActualMySQLOperatingHoursRecord() {
        Optional<OrderBusinessSettings> optionalSettings = repository.findFirstByOrderByIdAsc();
        assertThat(optionalSettings).isPresent();

        OrderBusinessSettings settings = optionalSettings.get();
        assertThat(settings.getOrderAcceptanceEnabled()).isTrue();
        assertThat(settings.getOpeningTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(settings.getClosingTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(settings.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(settings.getDeliveryType()).isEqualTo(DeliveryType.NEXT_DAY);
        assertThat(settings.getDeliveryDaysOffset()).isEqualTo(1);
    }

    @Test
    @DisplayName("05:59 IST - Should REJECT order before opening time")
    void testOrderAcceptanceAt0559IST() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 5, 59, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed")
                .hasMessageContaining("6:00 AM")
                .hasMessageContaining("10:00 PM");
    }

    @Test
    @DisplayName("06:00 IST - Should ACCEPT order at opening time (inclusive boundary)")
    void testOrderAcceptanceAt0600IST() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 6, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("12:00 IST - Should ACCEPT order at midday")
    void testOrderAcceptanceAt1200IST() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 12, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("13:13 IST (1:13 PM) - Order acceptance MUST BE OPEN")
    void testOrderAcceptanceAt1313IST() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 13, 13, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Must be OPEN without exception
    }

    @Test
    @DisplayName("21:59 IST (9:59 PM) - Should ACCEPT order just before closing cutoff")
    void testOrderAcceptanceAt2159IST() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 21, 59, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        service.validateOrderAcceptanceWindow(); // Should not throw
    }

    @Test
    @DisplayName("22:00 IST (10:00 PM) - Should REJECT order at exact closing cutoff (exclusive boundary)")
    void testOrderAcceptanceAt2200IST() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 22, 0, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed")
                .hasMessageContaining("6:00 AM")
                .hasMessageContaining("10:00 PM");
    }

    @Test
    @DisplayName("22:01 IST (10:01 PM) - Should REJECT order after closing time")
    void testOrderAcceptanceAt2201IST() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 21, 22, 1, 0);
        OrderBusinessSettingsService service = createServiceWithFixedClock(time, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(service::validateOrderAcceptanceWindow)
                .isInstanceOf(OrderCutoffException.class)
                .hasMessageContaining("Order placement is currently closed")
                .hasMessageContaining("6:00 AM")
                .hasMessageContaining("10:00 PM");
    }

    @Test
    @DisplayName("Verify API response serialization: openingTime=06:00, closingTime=22:00")
    void testApiResponseSerialization() throws Exception {
        OrderBusinessSettingsService service = createServiceWithFixedClock(
                LocalDateTime.of(2026, 11, 21, 13, 13, 0), ZoneId.of("Asia/Kolkata")
        );
        OrderBusinessSettingsResponse response = service.getSettings();

        assertThat(response.isOrderAcceptanceEnabled()).isTrue();
        assertThat(response.getOpeningTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(response.getClosingTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(response.getTimezone()).isEqualTo("Asia/Kolkata");

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"openingTime\":\"06:00\"");
        assertThat(json).contains("\"closingTime\":\"22:00\"");
        assertThat(json).contains("\"timezone\":\"Asia/Kolkata\"");
        assertThat(json).contains("\"orderAcceptanceEnabled\":true");
    }
}
