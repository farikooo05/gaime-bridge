package com.dynorix.gaimebridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.exports")
public record ExportStorageProperties(
        String baseDir
) {
}
