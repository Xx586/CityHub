package com.cityhub.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheClientTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final CacheClient client;
    private final RedissonClient redisson = mock(RedissonClient.class);

    CacheClientTest() {
        when(redis.opsForValue()).thenReturn(values);
        client = new CacheClient(redis, new ObjectMapper().findAndRegisterModules(),
                redisson, new SyncTaskExecutor(), new SimpleMeterRegistry());
    }

    @Test
    void cacheHitSkipsDatabase() {
        when(values.get("shop:1")).thenReturn("{\"name\":\"cached\"}");
        AtomicInteger loads = new AtomicInteger();

        Sample result = client.queryWithPassThrough("shop:1", Sample.class,
                ignored -> { loads.incrementAndGet(); return new Sample("db"); },
                Duration.ofMinutes(1), Duration.ofSeconds(10));

        assertThat(result.name()).isEqualTo("cached");
        assertThat(loads).hasValue(0);
    }

    @Test
    void nullValueIsCachedAgainstPenetration() {
        when(values.get("shop:404")).thenReturn(null);
        assertThat(client.queryWithPassThrough("shop:404", Sample.class, ignored -> null,
                Duration.ofMinutes(1), Duration.ofSeconds(10))).isNull();
        verify(values).set(eq("shop:404"), eq("__NULL__"), eq(Duration.ofSeconds(10)));

        when(values.get("shop:404")).thenReturn("__NULL__");
        client.queryWithPassThrough("shop:404", Sample.class, ignored -> new Sample("unexpected"),
                Duration.ofMinutes(1), Duration.ofSeconds(10));
        verify(values, never()).set(eq("shop:404"), eq("unexpected"), any(Duration.class));
    }

    @Test
    void databaseValueIsCachedWithJitteredTtl() {
        when(values.get("shop:2")).thenReturn(null);
        Sample result = client.queryWithPassThrough("shop:2", Sample.class,
                ignored -> new Sample("database"), Duration.ofMinutes(10), Duration.ofSeconds(10));
        assertThat(result.name()).isEqualTo("database");
        verify(values).set(eq("shop:2"), eq("{\"name\":\"database\"}"), any(Duration.class));
    }

    @Test
    void staleLogicalValueIsReturnedWhileSingleThreadRebuilds() {
        String stale = "{\"data\":{\"name\":\"stale\"},\"expireAt\":\""
                + LocalDateTime.now().minusMinutes(1) + "\"}";
        when(values.get("hot:1")).thenReturn(stale);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("lock:cache:rebuild:hot:1")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        Sample result = client.queryWithLogicalExpire("hot:1", Sample.class,
                ignored -> new Sample("fresh"), Duration.ofMinutes(30));

        assertThat(result.name()).isEqualTo("stale");
        verify(values).set(eq("hot:1"), org.mockito.ArgumentMatchers.contains("fresh"), any(Duration.class));
        verify(lock).unlock();
    }

    record Sample(String name) {}
}
