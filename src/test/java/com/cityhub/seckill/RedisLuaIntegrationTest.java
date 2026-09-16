package com.cityhub.seckill;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisLuaIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedis() {
        connectionFactory.destroy();
    }

    @Test
    void seckillScriptChecksTimeStockAndOneOrderAtomically() {
        DefaultRedisScript<Long> script = script("lua/seckill.lua");
        long now = System.currentTimeMillis();
        long voucherId = 100L;
        List<String> keys = keys(voucherId, 9001L);
        redis.opsForValue().set(keys.get(0), "1");
        redis.opsForValue().set(keys.get(2), Long.toString(now + 60_000));
        redis.opsForValue().set(keys.get(3), Long.toString(now + 120_000));

        assertThat(executeSeckill(script, keys, voucherId, 7L, 9001L)).isEqualTo(3L);

        redis.opsForValue().set(keys.get(2), Long.toString(now - 60_000));
        assertThat(executeSeckill(script, keys, voucherId, 7L, 9001L)).isZero();
        assertThat(redis.opsForValue().get(keys.get(0))).isEqualTo("0");
        assertThat(redis.opsForValue().get(keys.get(5))).isEqualTo("PENDING:7");
        assertThat(redis.opsForStream().size(SeckillKeys.OUTBOX_STREAM)).isEqualTo(1L);

        redis.opsForValue().set(keys.get(0), "1");
        assertThat(executeSeckill(script, keys(voucherId, 9002L), voucherId, 7L, 9002L)).isEqualTo(2L);
        assertThat(executeSeckill(script, keys(voucherId, 9003L), voucherId, 8L, 9003L)).isZero();
        assertThat(executeSeckill(script, keys(voucherId, 9004L), voucherId, 9L, 9004L)).isEqualTo(1L);
    }

    @Test
    void slidingWindowRejectsRequestBeyondLimit() {
        DefaultRedisScript<Long> script = script("lua/sliding_window_rate_limit.lua");
        String key = "test:rate:" + UUID.randomUUID();
        assertThat(redis.execute(script, List.of(key), "60", "2", "a")).isEqualTo(1L);
        assertThat(redis.execute(script, List.of(key), "60", "2", "b")).isZero();
        assertThat(redis.execute(script, List.of(key), "60", "2", "c")).isEqualTo(-1L);
    }

    private static Long executeSeckill(DefaultRedisScript<Long> script, List<String> keys,
                                        long voucherId, long userId, long orderId) {
        return redis.execute(script, keys, Long.toString(voucherId), Long.toString(userId),
                Long.toString(orderId), UUID.randomUUID().toString(), Instant.now().toString());
    }

    private static List<String> keys(long voucherId, long orderId) {
        return List.of(SeckillKeys.stock(voucherId), SeckillKeys.orders(voucherId),
                SeckillKeys.begin(voucherId), SeckillKeys.end(voucherId),
                SeckillKeys.OUTBOX_STREAM, SeckillKeys.orderStatus(orderId));
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
