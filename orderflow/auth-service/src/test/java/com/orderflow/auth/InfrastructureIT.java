package com.orderflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.auth.platform.PlatformMetadata;
import com.orderflow.auth.platform.PlatformMetadataRepository;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class InfrastructureIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("orderflow_auth_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.fail-fast", () -> "true");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @Autowired
    private PlatformMetadataRepository metadataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void infrastructureIsMigratedValidatedAndReachable() throws Exception {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(migrationCount).isGreaterThanOrEqualTo(1);

        PlatformMetadata metadata = metadataRepository.findByServiceName("auth-service")
                .orElseThrow();
        assertThat(metadata.getSchemaVersion()).isEqualTo("1");
        assertThat(metadata.getCreatedAt()).isNotNull();

        redisTemplate.opsForValue().set("orderflow:test", "ready", Duration.ofSeconds(30));
        assertThat(redisTemplate.opsForValue().get("orderflow:test")).isEqualTo("ready");

        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
                "5000"))) {
            assertThat(admin.describeCluster().clusterId().get(10, TimeUnit.SECONDS))
                    .isNotBlank();
        }
    }
}
