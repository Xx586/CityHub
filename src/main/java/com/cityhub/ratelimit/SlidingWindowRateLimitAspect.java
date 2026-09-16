package com.cityhub.ratelimit;

import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import com.cityhub.security.UserContext;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class SlidingWindowRateLimitAspect {
    private static final DefaultRedisScript<Long> SCRIPT;
    private static final ExpressionParser SPEL = new SpelExpressionParser();

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("lua/sliding_window_rate_limit.lua"));
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Around("@annotation(rule)")
    public Object enforce(ProceedingJoinPoint joinPoint, SlidingWindowRateLimit rule) throws Throwable {
        String identity = identity(joinPoint, rule);
        String redisKey = "rate:limit:" + rule.resource() + ":" + identity;
        Long remaining = redisTemplate.execute(SCRIPT, List.of(redisKey),
                Integer.toString(rule.windowSeconds()),
                Integer.toString(rule.limit()),
                UUID.randomUUID().toString());
        if (remaining == null || remaining < 0) {
            meterRegistry.counter("cityhub.ratelimit.rejected", "resource", rule.resource()).increment();
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return joinPoint.proceed();
    }

    private String identity(ProceedingJoinPoint joinPoint, SlidingWindowRateLimit rule) {
        String base = switch (rule.dimension()) {
            case GLOBAL -> "global";
            case IP -> "ip:" + clientIp();
            case USER -> "user:" + UserContext.requireUserId();
            case BUSINESS -> "business";
        };
        if (!StringUtils.hasText(rule.key())) {
            return base;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        EvaluationContext context = new StandardEvaluationContext();
        String[] names = signature.getParameterNames();
        Object[] values = joinPoint.getArgs();
        for (int i = 0; i < names.length; i++) {
            context.setVariable(names[i], values[i]);
        }
        Object suffix = SPEL.parseExpression(rule.key()).getValue(context);
        return base + ":" + String.valueOf(suffix);
    }

    private String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        return StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}

