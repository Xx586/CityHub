package com.cityhub.seckill;

import com.cityhub.domain.entity.VoucherOrder;
import com.cityhub.domain.enums.VoucherOrderStatus;
import com.cityhub.mapper.SeckillVoucherMapper;
import com.cityhub.mapper.VoucherOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {
    private final VoucherOrderMapper orderMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;

    @Transactional
    public void createOrderIdempotently(VoucherOrderEvent event) {
        if (orderMapper.selectById(event.orderId()) != null) {
            return;
        }
        if (seckillVoucherMapper.decrementStock(event.voucherId()) != 1) {
            throw new IllegalStateException("Database stock is insufficient for voucher " + event.voucherId());
        }
        VoucherOrder order = new VoucherOrder();
        order.setId(event.orderId());
        order.setUserId(event.userId());
        order.setVoucherId(event.voucherId());
        order.setPayType(1);
        order.setStatus(VoucherOrderStatus.UNPAID);
        orderMapper.insert(order);
    }
}

