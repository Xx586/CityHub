package com.cityhub;

import com.cityhub.config.KafkaTopics;
import com.cityhub.mapper.ShopMapper;
import com.cityhub.seckill.SeckillKeys;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "cityhub.order.unpaid-timeout=24h")
@Testcontainers(disabledWithoutDocker = true)
class FullStackSmokeTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("cityhub").withUsername("cityhub").withPassword("cityhub_test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1"));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    ShopMapper shopMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    KafkaTopics topics;

    @Test
    void flywayRedisInitializationAndKafkaTopicsAreReady() throws Exception {
        assertThat(shopMapper.selectCount(null)).isGreaterThanOrEqualTo(2);
        assertThat(redisTemplate.opsForValue().get(SeckillKeys.stock(1L))).isEqualTo("100");

        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            assertThat(admin.listTopics().names().get(Duration.ofSeconds(10).toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS))
                    .contains(topics.orderTopic(), topics.orderDltTopic());
        }
    }
}
