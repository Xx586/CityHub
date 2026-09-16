package com.cityhub.seckill;

import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import com.cityhub.ratelimit.RateLimitDimension;
import com.cityhub.ratelimit.SlidingWindowRateLimit;
import com.cityhub.security.UserContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SeckillService {
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final RedisIdGenerator idGenerator;
    private final MeterRegistry meterRegistry;

    public SeckillService(StringRedisTemplate redisTemplate,
                           RedisIdGenerator idGenerator,
                           MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
        this.meterRegistry = meterRegistry;
    }

    @SlidingWindowRateLimit(resource = "seckill", dimension = RateLimitDimension.USER,
            key = "#voucherId", limit = 5, windowSeconds = 1)
    public SeckillResponse seckill(Long voucherId) {
        Long userId = UserContext.requireUserId();
        long orderId = idGenerator.nextId("order");
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                List.of(
                        SeckillKeys.stock(voucherId),
                        SeckillKeys.orders(voucherId),
                        SeckillKeys.begin(voucherId),
                        SeckillKeys.end(voucherId),
                        SeckillKeys.OUTBOX_STREAM,
                        SeckillKeys.orderStatus(orderId)),
                voucherId.toString(), userId.toString(), Long.toString(orderId), eventId, now.toString());
        if (result == null) {
            throw new IllegalStateException("Redis did not return a seckill result");
        }
        meterRegistry.counter("cityhub.seckill.requests", "result", resultName(result)).increment();
        if (result != 0) {
            throw switch (result.intValue()) {
                case 1 -> new BusinessException(ErrorCode.SOLD_OUT);
                case 2 -> new BusinessException(ErrorCode.DUPLICATE_ORDER);
                case 3 -> new BusinessException(ErrorCode.SECKILL_NOT_STARTED);
                case 4 -> new BusinessException(ErrorCode.SECKILL_ENDED);
                default -> new BusinessException(ErrorCode.NOT_FOUND, "秒杀优惠券不存在或尚未初始化");
            };
        }
        return new SeckillResponse(orderId, "PENDING");
    }

    private String resultName(long result) {
        return switch ((int) result) {
            case 0 -> "accepted";
            case 1 -> "sold-out";
            case 2 -> "duplicate";
            case 3 -> "not-started";
            case 4 -> "ended";
            default -> "not-initialized";
        };
    }
}

