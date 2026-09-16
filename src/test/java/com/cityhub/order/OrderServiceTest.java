package com.cityhub.order;

import com.cityhub.common.BusinessException;
import com.cityhub.domain.entity.VoucherOrder;
import com.cityhub.domain.enums.UserRole;
import com.cityhub.domain.enums.VoucherOrderStatus;
import com.cityhub.mapper.VoucherOrderMapper;
import com.cityhub.security.AuthUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class OrderServiceTest {
    private final VoucherOrderMapper mapper = mock(VoucherOrderMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final OrderService service = new OrderService(mapper, redis);

    OrderServiceTest() {
        AuthUser user = new AuthUser(7L, "user", "", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(redis.opsForValue()).thenReturn(values);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsPendingStatusBeforeKafkaPersistsOrder() {
        when(values.get("seckill:order:status:99")).thenReturn("PENDING:7");
        OrderView view = service.findMine(99L);
        assertThat(view.status()).isNull();
        assertThat(view.processingStatus()).isEqualTo("PENDING");
    }

    @Test
    void refusesToExposeAnotherUsersPendingOrder() {
        when(values.get("seckill:order:status:99")).thenReturn("PENDING:8");
        assertThatThrownBy(() -> service.findMine(99L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void readsPersistedOrderOwnedByCurrentUser() {
        VoucherOrder order = order(99L, 7L);
        order.setStatus(VoucherOrderStatus.UNPAID);
        when(mapper.selectById(99L)).thenReturn(order);

        OrderView view = service.findMine(99L);
        assertThat(view.id()).isEqualTo(99L);
        assertThat(view.status()).isEqualTo(VoucherOrderStatus.UNPAID);
        assertThat(view.processingStatus()).isEqualTo("CREATED");
    }

    @Test
    void missingAndForeignPersistedOrdersAreHidden() {
        when(mapper.selectById(99L)).thenReturn(order(99L, 8L));
        assertThatThrownBy(() -> service.findMine(99L)).isInstanceOf(BusinessException.class);

        when(mapper.selectById(99L)).thenReturn(null);
        when(values.get("seckill:order:status:99")).thenReturn(null);
        assertThatThrownBy(() -> service.findMine(99L)).isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void paginatesOnlyCurrentUsersOrdersWithSafeBounds() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<VoucherOrder> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of(order(5L, 7L)));
            page.setTotal(1);
            return page;
        });
        PageResult<OrderView> result = service.findMine(0, 100);
        assertThat(result.records()).hasSize(1);
        assertThat(result.current()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(50);
    }

    @Test
    void payUsesConditionalUpdateAsConcurrencyGuard() {
        VoucherOrder order = order(99L, 7L);
        order.setStatus(VoucherOrderStatus.UNPAID);
        when(mapper.selectById(99L)).thenReturn(order);
        when(mapper.markPaid(99L, 7L)).thenReturn(1);
        service.simulatePayment(99L);

        when(mapper.markPaid(99L, 7L)).thenReturn(0);
        assertThatThrownBy(() -> service.simulatePayment(99L)).isInstanceOf(BusinessException.class);
    }

    private VoucherOrder order(long id, long userId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setVoucherId(1L);
        return order;
    }
}
