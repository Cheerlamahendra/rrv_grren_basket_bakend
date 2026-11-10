package com.rrvgreenbasket.store.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.store.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.store.entity.StoreSettings;
import com.rrvgreenbasket.store.repository.StoreSettingsAuditRepository;
import com.rrvgreenbasket.store.repository.StoreSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoreControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreSettingsRepository storeSettingsRepository;

    @Autowired
    private StoreSettingsAuditRepository auditRepository;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        storeSettingsRepository.deleteAll();

        StoreSettings initial = StoreSettings.builder()
                .publicId(UUID.randomUUID().toString())
                .storeName("RRV Green Basket")
                .brandTagline("Freshness Delivered Daily")
                .supportEmail("rrvgreenbasket@gmail.com")
                .supportPhone("+91 98765 43210")
                .physicalAddress("Kurnool, Andhra Pradesh, India")
                .websiteUrl("https://www.rrvgreenbasket.in")
                .isActive(true)
                .updatedBy("SYSTEM")
                .build();
        storeSettingsRepository.save(initial);
    }

    @Test
    @DisplayName("1. Public visitor can get public store settings without authentication")
    void testPublicStoreSettings() throws Exception {
        mockMvc.perform(get("/api/store/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("RRV Green Basket"))
                .andExpect(jsonPath("$.data.supportEmail").value("rrvgreenbasket@gmail.com"))
                // Ensure internal DB keys and audit fields are NEVER exposed publicly
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.logoS3Key").doesNotExist())
                .andExpect(jsonPath("$.data.faviconS3Key").doesNotExist());
    }

    @Test
    @DisplayName("2. Admin retrieves and updates general store settings")
    void testAdminStoreSettingsUpdate() throws Exception {
        // Admin GET
        mockMvc.perform(get("/api/admin/store/settings")
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("RRV Green Basket"));

        // Admin PUT update
        UpdateStoreSettingsRequest updateReq = UpdateStoreSettingsRequest.builder()
                .storeName("RRV Green Basket Super")
                .brandTagline("Organic Vegetables Direct from Farms")
                .supportEmail("contact@rrvgreenbasket.in")
                .supportPhone("+91 98765 00000")
                .physicalAddress("Kurnool Town, Andhra Pradesh")
                .websiteUrl("https://www.rrvgreenbasket.in")
                .build();

        mockMvc.perform(put("/api/admin/store/settings")
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("RRV Green Basket Super"))
                .andExpect(jsonPath("$.data.supportEmail").value("contact@rrvgreenbasket.in"));
    }

    @Test
    @DisplayName("3. Admin uploads valid PNG logo to AWS S3 prefix branding/logo/")
    void testUploadLogo() throws Exception {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
        MockMultipartFile file = new MockMultipartFile("file", "brand-logo.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/admin/store/assets/logo")
                        .file(file)
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetType").value("LOGO"))
                .andExpect(jsonPath("$.data.logoUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.uploaded").value(true));

        StoreSettings settings = storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc().orElseThrow();
        assertThat(settings.getLogoS3Key()).startsWith("branding/logo/");
        assertThat(settings.getLogoUrl()).contains("branding/logo/");
    }

    @Test
    @DisplayName("4. Admin uploads valid ICO favicon to AWS S3 prefix branding/favicon/")
    void testUploadFavicon() throws Exception {
        byte[] icoBytes = new byte[]{0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x20, 0x20};
        MockMultipartFile file = new MockMultipartFile("file", "favicon.ico", "image/x-icon", icoBytes);

        mockMvc.perform(multipart("/api/admin/store/assets/favicon")
                        .file(file)
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetType").value("FAVICON"))
                .andExpect(jsonPath("$.data.faviconUrl").isNotEmpty());

        StoreSettings settings = storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc().orElseThrow();
        assertThat(settings.getFaviconS3Key()).startsWith("branding/favicon/");
    }

    @Test
    @DisplayName("5. Admin deletes logo and favicon")
    void testDeleteAssets() throws Exception {
        // Delete logo
        mockMvc.perform(delete("/api/admin/store/assets/logo")
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Delete favicon
        mockMvc.perform(delete("/api/admin/store/assets/favicon")
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("6. Customer is forbidden from modifying store settings or uploading assets")
    void testCustomerForbidden() throws Exception {
        UpdateStoreSettingsRequest updateReq = UpdateStoreSettingsRequest.builder()
                .storeName("Hacked Store")
                .supportEmail("hacker@example.com")
                .supportPhone("+91 99999 99999")
                .physicalAddress("Unknown")
                .websiteUrl("https://hacked.com")
                .build();

        mockMvc.perform(put("/api/admin/store/settings")
                        .header("X-User-Id", "CUST_1")
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. Malicious SVG with embedded JavaScript is rejected")
    void testMaliciousSvgRejected() throws Exception {
        String xssSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert('XSS')</script></svg>";
        MockMultipartFile file = new MockMultipartFile("file", "malicious.svg", "image/svg+xml",
                xssSvg.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/store/assets/logo")
                        .file(file)
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
