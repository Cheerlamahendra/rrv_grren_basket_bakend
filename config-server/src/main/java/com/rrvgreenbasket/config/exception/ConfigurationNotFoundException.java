package com.rrvgreenbasket.config.exception;

/**
 * ConfigurationNotFoundException
 *
 * <p><strong>What it does:</strong></p>
 * Custom unchecked exception thrown when requested configuration file or service profile
 * cannot be resolved by the Config Server.
 */
public class ConfigurationNotFoundException extends RuntimeException {

    public ConfigurationNotFoundException(String message) {
        super(message);
    }

    public ConfigurationNotFoundException(String application, String profile) {
        super(String.format("Configuration not found for application '%s' with profile '%s'", application, profile));
    }
}
