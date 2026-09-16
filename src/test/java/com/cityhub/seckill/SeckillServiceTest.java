package com.cityhub.seckill;

import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import com.cityhub.domain.enums.UserRole;
import com.cityhub.security.AuthUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeckillServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisIdGenerator ids = mock(RedisIdGenerator.class);
    private final SeckillService service = new SeckillService(redis, ids, new SimpleMeterRegistry());

    SeckillServiceTest() {
        AuthUser user = new AuthUser(9L, "tester", "", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(ids.nextId("order")).thenReturn(88L);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptedRequestReturnsPendingOrderId() {
        result(0L);
        assertThat(service.seckill(1L)).isEqualTo(new SeckillResponse(88L, "PENDING"));
    }

    @Test
    void mapsLuaResultsToStableBusinessErrors() {
        assertError(1L, ErrorCode.SOLD_OUT);
        assertError(2L, ErrorCode.DUPLICATE_ORDER);
        assertError(3L, ErrorCode.SECKILL_NOT_STARTED);
        assertError(4L, ErrorCode.SECKILL_ENDED);
        assertError(5L, ErrorCode.NOT_FOUND);
    }

    private void assertError(long result, ErrorCode code) {
        result(result);
        assertThatThrownBy(() -> service.seckill(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    @SuppressWarnings("unchecked")
    private void result(long result) {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(Object[].class))).thenReturn(result);
    }
}
