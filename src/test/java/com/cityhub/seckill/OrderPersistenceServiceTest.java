package com.cityhub.seckill;

import com.cityhub.domain.entity.VoucherOrder;
import com.cityhub.domain.enums.VoucherOrderStatus;
import com.cityhub.mapper.SeckillVoucherMapper;
import com.cityhub.mapper.VoucherOrderMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPersistenceServiceTest {
    private final VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
    private final SeckillVoucherMapper stockMapper = mock(SeckillVoucherMapper.class);
    private final OrderPersistenceService service = new OrderPersistenceService(orderMapper, stockMapper);
    private final VoucherOrderEvent event = new VoucherOrderEvent(
            UUID.randomUUID().toString(), 1, 1001L, 11L, 21L, Instant.now());

    @Test
    void duplicateEventDoesNotDeductStockAgain() {
        when(orderMapper.selectById(1001L)).thenReturn(new VoucherOrder());
        service.createOrderIdempotently(event);
        verify(stockMapper, never()).decrementStock(any());
        verify(orderMapper, never()).insert(any(VoucherOrder.class));
    }

    @Test
    void createsUnpaidOrderAfterConditionalStockDeduction() {
        when(stockMapper.decrementStock(21L)).thenReturn(1);
        service.createOrderIdempotently(event);

        verify(orderMapper).insert(org.mockito.ArgumentMatchers.argThat((VoucherOrder order) ->
                order.getId().equals(1001L)
                        && order.getUserId().equals(11L)
                        && order.getStatus() == VoucherOrderStatus.UNPAID));
    }

    @Test
    void insufficientDatabaseStockFailsForKafkaRetry() {
        when(stockMapper.decrementStock(21L)).thenReturn(0);
        assertThatThrownBy(() -> service.createOrderIdempotently(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient");
        verify(orderMapper, never()).insert(any(VoucherOrder.class));
    }
}
