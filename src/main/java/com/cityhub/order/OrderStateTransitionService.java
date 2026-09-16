package com.cityhub.order;

import com.cityhub.domain.entity.OutboxEvent;
import com.cityhub.domain.entity.VoucherOrder;
import com.cityhub.domain.enums.OutboxStatus;
import com.cityhub.mapper.OutboxEventMapper;
import com.cityhub.mapper.SeckillVoucherMapper;
import com.cityhub.mapper.VoucherOrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderStateTransitionService {
    public static final String STOCK_RELEASE_EVENT = "ORDER_STOCK_RELEASE";

    private final VoucherOrderMapper orderMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean closeIfUnpaid(Long orderId) {
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null || orderMapper.markCanceled(orderId) != 1) {
            return false;
        }
        seckillVoucherMapper.incrementStock(order.getVoucherId());

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("VOUCHER_ORDER");
        event.setAggregateId(orderId.toString());
        event.setEventType(STOCK_RELEASE_EVENT);
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        try {
            event.setPayload(objectMapper.writeValueAsString(
                    new StockReleaseEvent(orderId, order.getUserId(), order.getVoucherId())));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize stock release event", exception);
        }
        outboxMapper.insert(event);
        return true;
    }
}

