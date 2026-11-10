package com.rrvgreenbasket.store.entity;

import com.rrvgreenbasket.store.enums.StoreAuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "store_settings_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSettingsAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_settings_public_id", nullable = false, length = 36)
    private String storeSettingsPublicId;

    @Column(name = "actor_id", nullable = false, length = 50)
    private String actorId;

    @Column(name = "actor_role", nullable = false, length = 50)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private StoreAuditAction action;

    @Column(name = "changed_fields", columnDefinition = "TEXT")
    private String changedFields;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
