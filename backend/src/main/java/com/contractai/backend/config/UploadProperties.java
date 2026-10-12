package com.contractai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Binds "app.upload". DataSize understands values like 10MB. */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(DataSize maxFileSize) {
}
