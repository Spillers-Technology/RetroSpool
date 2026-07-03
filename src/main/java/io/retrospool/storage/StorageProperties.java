package io.retrospool.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Landing object store settings. Empty endpoint = real AWS; MinIO/Backblaze need
 * an endpoint override + path-style access.
 */
@ConfigurationProperties(prefix = "gateway.storage")
public record StorageProperties(
        String bucket,
        String region,
        String endpoint,
        boolean pathStyleAccess,
        String accessKey,
        String secretKey) {
}
