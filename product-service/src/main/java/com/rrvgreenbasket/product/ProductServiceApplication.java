package com.rrvgreenbasket.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ProductServiceApplication
 *
 * <p><strong>What it does:</strong></p>
 * Entry point for RRV Green Basket Product Microservice managing catalog categories,
 * per-KG pricing models, S3 product media, and stock availability integration.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
