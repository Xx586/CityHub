package com.cityhub.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SlidingWindowRateLimit {
    String resource();
    int limit();
    int windowSeconds();
    RateLimitDimension dimension() default RateLimitDimension.GLOBAL;
    String key() default "";
}

