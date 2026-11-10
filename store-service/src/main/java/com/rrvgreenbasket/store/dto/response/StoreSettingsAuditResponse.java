package com.rrvgreenbasket.store.dto.response;

import com.rrvgreenbasket.store.enums.StoreAuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSettingsAuditResponse {

    private Long id;
    private String storeSettingsPublicId;
    private String actorId;
    private String actorRole;
    private StoreAuditAction action;
    private String changedFields;
    private Instant createdAt;
}
