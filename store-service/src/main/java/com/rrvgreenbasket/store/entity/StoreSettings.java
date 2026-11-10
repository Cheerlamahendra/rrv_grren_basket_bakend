package com.rrvgreenbasket.store.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "store_name", nullable = false, length = 150)
    private String storeName;

    @Column(name = "brand_tagline", length = 255)
    private String brandTagline;

    @Column(name = "support_email", nullable = false, length = 150)
    private String supportEmail;

    @Column(name = "support_phone", nullable = false, length = 50)
    private String supportPhone;

    @Column(name = "physical_address", nullable = false, length = 500)
    private String physicalAddress;

    @Column(name = "website_url", nullable = false, length = 255)
    private String websiteUrl;

    @Column(name = "logo_file_name", length = 255)
    private String logoFileName;

    @Column(name = "logo_s3_key", length = 500)
    private String logoS3Key;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "favicon_file_name", length = 255)
    private String faviconFileName;

    @Column(name = "favicon_s3_key", length = 500)
    private String faviconS3Key;

    @Column(name = "favicon_url", length = 500)
    private String faviconUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
    }
}
