package com.contractai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the "app.storage" section of application.yml. */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String location) {
}
