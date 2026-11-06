package com.rrvgreenbasket.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@EnableAutoConfiguration(exclude = {
        EurekaClientAutoConfiguration.class,
        EurekaDiscoveryClientConfiguration.class
})
@ActiveProfiles("test")
class InventoryServiceApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("1. Context loads successfully and DataSource is healthy")
    void contextLoads() throws SQLException {
        assertThat(dataSource).isNotNull();
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isClosed()).isFalse();
        }
    }

    @Test
    @DisplayName("2. Flyway migration V1 executed and created all 6 tables")
    void verifyFlywaySchemaCreatedTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES",
                String.class
        );

        List<String> lowerTables = tables.stream().map(String::toLowerCase).toList();

        assertThat(lowerTables).contains(
                "inventory_items",
                "stock_movements",
                "stock_reservation_requests",
                "stock_reservation_items",
                "stock_alerts",
                "inventory_event_outbox"
        );
    }

    @Test
    @DisplayName("3. Verify inventory_items table structure and decimal precision")
    void verifyInventoryItemsColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = 'INVENTORY_ITEMS'",
                String.class
        );

        List<String> lowerColumns = columns.stream().map(String::toLowerCase).toList();

        assertThat(lowerColumns).contains(
                "id",
                "public_id",
                "product_public_id",
                "unit_of_measure",
                "physical_quantity",
                "reserved_quantity",
                "available_quantity",
                "low_stock_threshold",
                "initialized",
                "version",
                "created_at",
                "updated_at"
        );
    }

    @Test
    @DisplayName("4. Verify stock_movements columns include before/after physical and available quantities")
    void verifyStockMovementsColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = 'STOCK_MOVEMENTS'",
                String.class
        );

        List<String> lowerColumns = columns.stream().map(String::toLowerCase).toList();

        assertThat(lowerColumns).contains(
                "id",
                "public_id",
                "product_public_id",
                "movement_type",
                "quantity",
                "physical_quantity_before",
                "physical_quantity_after",
                "available_quantity_before",
                "available_quantity_after",
                "unit_of_measure",
                "reason",
                "reference_type",
                "reference_id",
                "notes",
                "performed_by",
                "created_at"
        );
    }

    @Test
    @DisplayName("5. Verify reservation parent and item child table structures")
    void verifyReservationTables() {
        List<String> reqColumns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = 'STOCK_RESERVATION_REQUESTS'",
                String.class
        );
        List<String> itemColumns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = 'STOCK_RESERVATION_ITEMS'",
                String.class
        );

        assertThat(reqColumns.stream().map(String::toLowerCase).toList()).contains(
                "id", "public_id", "order_public_id", "idempotency_key", "status", "expires_at", "created_at", "updated_at"
        );
        assertThat(itemColumns.stream().map(String::toLowerCase).toList()).contains(
                "id", "public_id", "reservation_request_id", "order_public_id", "product_public_id", "reserved_quantity", "unit_of_measure", "item_status"
        );
    }
}
