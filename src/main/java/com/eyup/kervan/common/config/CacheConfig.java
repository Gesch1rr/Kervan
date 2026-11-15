package com.eyup.kervan.common.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@EnableCaching
@Configuration(proxyBeanMethods = false)
public class CacheConfig {

    // Defining this bean replaces the spring.cache.redis.* defaults, so everything is set here.
    @Bean
    RedisCacheConfiguration redisCacheConfiguration(CacheTtlProperties properties) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("kervan:")
                .entryTtl(properties.defaultTtl())
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()));
    }

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheManagerCustomizer(RedisCacheConfiguration defaults,
                                                                   CacheTtlProperties properties) {
        return builder -> {
            // Evictions and puts wait for the surrounding transaction to commit, so a reader cannot
            // re-cache data that is about to change.
            builder.transactionAware();
            properties.ttl().forEach((cacheName, ttl) ->
                    builder.withCacheConfiguration(cacheName, defaults.entryTtl(ttl)));
        };
    }

    // Stores the Java type next to each value so cached records come back as records, not Maps.
    // The validator only allows our own classes and JDK value/collection types to be deserialized.
    private static GenericJacksonJsonRedisSerializer jsonSerializer() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.eyup.kervan.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .build();
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }
}
