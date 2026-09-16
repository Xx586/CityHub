package com.cityhub.ratelimit;

import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlidingWindowRateLimitAspectTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SlidingWindowRateLimitAspect aspect = new SlidingWindowRateLimitAspect(
            redis, new SimpleMeterRegistry());

    @Test
    void allowedRequestProceedsWithBusinessParameterKey() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        SlidingWindowRateLimit rule = rule(RateLimitDimension.BUSINESS, "#voucherId");
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(Object[].class))).thenReturn(4L);
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.enforce(joinPoint, rule)).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void rejectedRequestReturnsRateLimitedError() {
        ProceedingJoinPoint joinPoint = joinPoint();
        SlidingWindowRateLimit rule = rule(RateLimitDimension.GLOBAL, "");
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(Object[].class))).thenReturn(-1L);

        assertThatThrownBy(() -> aspect.enforce(joinPoint, rule))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    private ProceedingJoinPoint joinPoint() {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"voucherId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{12L});
        return joinPoint;
    }

    private SlidingWindowRateLimit rule(RateLimitDimension dimension, String key) {
        SlidingWindowRateLimit rule = mock(SlidingWindowRateLimit.class);
        when(rule.resource()).thenReturn("test");
        when(rule.dimension()).thenReturn(dimension);
        when(rule.key()).thenReturn(key);
        when(rule.windowSeconds()).thenReturn(60);
        when(rule.limit()).thenReturn(5);
        return rule;
    }
}
