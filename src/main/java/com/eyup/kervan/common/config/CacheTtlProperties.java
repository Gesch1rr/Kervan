package com.eyup.kervan.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

// Per-cache TTLs live in YAML (kervan.cache.ttl.<cache-name>) so common/ does not need to know the modules.
@ConfigurationProperties("kervan.cache")
public record CacheTtlProperties(Duration defaultTtl, Map<String, Duration> ttl) {

    public CacheTtlProperties {
        defaultTtl = defaultTtl != null ? defaultTtl : Duration.ofMinutes(10);
        ttl = ttl != null ? ttl : Map.of();
    }
}
