package com.cityhub.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import com.cityhub.domain.entity.VoucherOrder;
import com.cityhub.mapper.VoucherOrderMapper;
import com.cityhub.security.UserContext;
import com.cityhub.seckill.SeckillKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final VoucherOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;

    public OrderView findMine(Long orderId) {
        Long userId = UserContext.requireUserId();
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            if (!userId.equals(order.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return view(order, "CREATED");
        }
        String asyncStatus = redisTemplate.opsForValue().get(SeckillKeys.orderStatus(orderId));
        if (asyncStatus == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        String[] parts = asyncStatus.split(":", 2);
        if (parts.length != 2 || !userId.toString().equals(parts[1])) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new OrderView(orderId, null, null, parts[0], null, null, null);
    }

    public PageResult<OrderView> findMine(long current, long size) {
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.min(50, Math.max(1, size));
        Page<VoucherOrder> page = orderMapper.selectPage(new Page<>(safeCurrent, safeSize),
                Wrappers.<VoucherOrder>lambdaQuery()
                        .eq(VoucherOrder::getUserId, UserContext.requireUserId())
                        .orderByDesc(VoucherOrder::getCreateTime));
        List<OrderView> records = page.getRecords().stream().map(order -> view(order, "CREATED")).toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public void simulatePayment(Long orderId) {
        Long userId = UserContext.requireUserId();
        if (orderMapper.markPaid(orderId, userId) != 1) {
            VoucherOrder order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在或尚未完成异步创建");
            }
            if (!userId.equals(order.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            throw new BusinessException(ErrorCode.CONFLICT,
                    "只有未支付订单可以支付，当前状态为 " + order.getStatus());
        }
    }

    private OrderView view(VoucherOrder order, String processingStatus) {
        return new OrderView(order.getId(), order.getVoucherId(), order.getStatus(), processingStatus,
                order.getCreateTime(), order.getPayTime(), order.getCancelTime());
    }
}

