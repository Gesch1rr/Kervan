package com.eyup.kervan;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    // These image names must match the images used in compose.yaml.
    private static final String POSTGRES_IMAGE = "postgres:18-alpine";
    private static final String RABBITMQ_IMAGE = "rabbitmq:4.4-management-alpine";
    private static final String REDIS_IMAGE = "redis:8.10-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse(RABBITMQ_IMAGE));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);
    }

}
