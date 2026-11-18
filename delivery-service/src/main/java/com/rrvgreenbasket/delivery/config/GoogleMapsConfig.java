package com.rrvgreenbasket.delivery.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class GoogleMapsConfig {

    private final GoogleMapsProperties googleMapsProperties;

    @Bean(name = "googlePlacesRestClient")
    public RestClient googlePlacesRestClient() {
        return RestClient.builder()
                .baseUrl(googleMapsProperties.getPlacesBaseUrl())
                .requestFactory(createClientHttpRequestFactory())
                .build();
    }

    @Bean(name = "googleGeocodingRestClient")
    public RestClient googleGeocodingRestClient() {
        return RestClient.builder()
                .baseUrl(googleMapsProperties.getGeocodingBaseUrl())
                .requestFactory(createClientHttpRequestFactory())
                .build();
    }

    @Bean(name = "googleRoutesRestClient")
    public RestClient googleRoutesRestClient() {
        return RestClient.builder()
                .baseUrl(googleMapsProperties.getRoutesBaseUrl())
                .requestFactory(createClientHttpRequestFactory())
                .build();
    }

    private SimpleClientHttpRequestFactory createClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(googleMapsProperties.getConnectTimeoutMs());
        factory.setReadTimeout(googleMapsProperties.getReadTimeoutMs());
        return factory;
    }
}
