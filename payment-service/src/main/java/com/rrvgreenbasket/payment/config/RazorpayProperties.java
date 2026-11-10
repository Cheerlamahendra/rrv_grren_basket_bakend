package com.rrvgreenbasket.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {

    private String keyId = "rzp_test_placeholder_key";
    private String keySecret = "placeholder_secret_key_not_real";
    private String webhookSecret = "placeholder_webhook_secret_not_real";
    private String currency = "INR";
    private boolean enabled = true;
}
