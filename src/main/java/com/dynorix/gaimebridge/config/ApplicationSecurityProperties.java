package com.dynorix.gaimebridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record ApplicationSecurityProperties(
        String apiUsername,
        String apiPassword
) {
}
