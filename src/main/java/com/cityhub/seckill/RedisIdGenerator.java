package com.cityhub.seckill;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RedisIdGenerator {
    private static final long BEGIN_TIMESTAMP = 1_735_689_600L; // 2025-01-01T00:00:00Z
    private static final int COUNT_BITS = 32;
    private final StringRedisTemplate redisTemplate;

    public long nextId(String business) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.BASIC_ISO_DATE);
        Long sequence = redisTemplate.opsForValue().increment("id:counter:" + business + ":" + date);
        if (sequence == null) {
            throw new IllegalStateException("Unable to allocate distributed id");
        }
        return (timestamp << COUNT_BITS) | sequence;
    }
}

