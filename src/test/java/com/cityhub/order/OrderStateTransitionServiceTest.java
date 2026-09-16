package com.cityhub.order;

import com.cityhub.domain.entity.OutboxEvent;
import com.cityhub.domain.entity.VoucherOrder;
import com.cityhub.domain.enums.OutboxStatus;
import com.cityhub.mapper.OutboxEventMapper;
import com.cityhub.mapper.SeckillVoucherMapper;
import com.cityhub.mapper.VoucherOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderStateTransitionServiceTest {
    private final VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
    private final SeckillVoucherMapper stockMapper = mock(SeckillVoucherMapper.class);
    private final OutboxEventMapper outboxMapper = mock(OutboxEventMapper.class);
    private final OrderStateTransitionService service = new OrderStateTransitionService(
            orderMapper, stockMapper, outboxMapper, new ObjectMapper());

    @Test
    void successfulCloseRestoresDatabaseStockAndWritesOutbox() {
        VoucherOrder order = new VoucherOrder();
        order.setId(1L);
        order.setUserId(2L);
        order.setVoucherId(3L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.markCanceled(1L)).thenReturn(1);

        assertThat(service.closeIfUnpaid(1L)).isTrue();
        verify(stockMapper).incrementStock(3L);
        verify(outboxMapper).insert(org.mockito.ArgumentMatchers.argThat((OutboxEvent event) ->
                event.getStatus() == OutboxStatus.PENDING
                        && event.getPayload().contains("\"orderId\":1")));
    }

    @Test
    void paymentWinnerMakesCloseNoOp() {
        VoucherOrder order = new VoucherOrder();
        order.setVoucherId(3L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.markCanceled(1L)).thenReturn(0);

        assertThat(service.closeIfUnpaid(1L)).isFalse();
        verify(stockMapper, never()).incrementStock(any());
        verify(outboxMapper, never()).insert(any(OutboxEvent.class));
    }
}
