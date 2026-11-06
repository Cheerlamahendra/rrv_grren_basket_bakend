package com.rrvgreenbasket.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CustomerServiceApplication
 *
 * <p><strong>What it does:</strong></p>
 * Entry point for the RRV Green Basket Customer Service microservice.
 * Manages customer profile lifecycle, contact details, account status,
 * and delivery addresses.
 *
 * <p><strong>Port:</strong> 8082</p>
 * <p><strong>Discovery:</strong> Eureka Client (registers as CUSTOMER-SERVICE)</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
