package com.eyup.kervan.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Fills @CreatedDate / @LastModifiedDate on BaseEntity.
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaConfig {
}
