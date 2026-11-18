package com.rrvgreenbasket.order.entity;

import com.rrvgreenbasket.order.enums.DeliveryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "order_business_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBusinessSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 36, nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "order_acceptance_enabled", nullable = false)
    @Builder.Default
    private Boolean orderAcceptanceEnabled = true;

    @Column(name = "opening_time", nullable = false)
    @Builder.Default
    private LocalTime openingTime = LocalTime.of(8, 0);

    @Column(name = "closing_time", nullable = false)
    @Builder.Default
    private LocalTime closingTime = LocalTime.of(21, 0);

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", length = 30, nullable = false)
    @Builder.Default
    private DeliveryType deliveryType = DeliveryType.NEXT_DAY;

    @Column(name = "delivery_days_offset", nullable = false)
    @Builder.Default
    private Integer deliveryDaysOffset = 1;

    @Column(name = "timezone", length = 50, nullable = false)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
