package com.cityhub.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private static final String NULL_VALUE = "__NULL__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final TaskExecutor cacheRebuildExecutor;
    private final MeterRegistry meterRegistry;

    public CacheClient(StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper,
                       RedissonClient redissonClient,
                       TaskExecutor cacheRebuildExecutor,
                       MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.cacheRebuildExecutor = cacheRebuildExecutor;
        this.meterRegistry = meterRegistry;
    }

    public <T> T queryWithPassThrough(String key, Class<T> type, Function<Void, T> fallback,
                                      Duration ttl, Duration nullTtl) {
        String json = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(json) && !NULL_VALUE.equals(json)) {
            meterRegistry.counter("cityhub.cache.requests", "layer", "redis", "result", "hit").increment();
            return fromJson(json, type);
        }
        if (NULL_VALUE.equals(json)) {
            meterRegistry.counter("cityhub.cache.requests", "layer", "redis", "result", "null-hit").increment();
            return null;
        }
        meterRegistry.counter("cityhub.cache.requests", "layer", "redis", "result", "miss").increment();
        T value = fallback.apply(null);
        if (value == null) {
            redisTemplate.opsForValue().set(key, NULL_VALUE, nullTtl);
            return null;
        }
        setJson(key, value, jitter(ttl, 60));
        return value;
    }

    public <T> T queryWithLogicalExpire(String key, Class<T> type, Function<Void, T> fallback,
                                        Duration logicalTtl) {
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            T value = fallback.apply(null);
            if (value != null) {
                setLogical(key, value, logicalTtl);
            }
            return value;
        }
        CacheEnvelope envelope = fromJson(json, CacheEnvelope.class);
        T value = objectMapper.convertValue(envelope.data(), type);
        if (envelope.expireAt().isAfter(LocalDateTime.now())) {
            meterRegistry.counter("cityhub.cache.requests", "layer", "logical", "result", "fresh").increment();
            return value;
        }

        meterRegistry.counter("cityhub.cache.requests", "layer", "logical", "result", "stale").increment();
        cacheRebuildExecutor.execute(() -> {
            RLock lock = redissonClient.getLock("lock:cache:rebuild:" + key);
            boolean acquired = false;
            try {
                acquired = lock.tryLock();
                if (acquired) {
                    T fresh = fallback.apply(null);
                    if (fresh != null) {
                        setLogical(key, fresh, logicalTtl);
                    }
                }
            } catch (RuntimeException exception) {
                log.error("Logical cache rebuild failed, key={}", key, exception);
            } finally {
                if (acquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
        return value;
    }

    public void evict(String key) {
        redisTemplate.delete(key);
    }

    public void setJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize cache value", exception);
        }
    }

    private void setLogical(String key, Object value, Duration logicalTtl) {
        CacheEnvelope envelope = new CacheEnvelope(objectMapper.valueToTree(value), LocalDateTime.now().plus(logicalTtl));
        setJson(key, envelope, logicalTtl.plus(Duration.ofHours(1)));
    }

    private Duration jitter(Duration base, int maxJitterSeconds) {
        return base.plusSeconds(ThreadLocalRandom.current().nextInt(maxJitterSeconds + 1));
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize cache value", exception);
        }
    }
}
