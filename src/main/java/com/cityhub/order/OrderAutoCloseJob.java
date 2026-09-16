package com.cityhub.order;

import com.cityhub.mapper.VoucherOrderMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoCloseJob {
    private final VoucherOrderMapper orderMapper;
    private final OrderStateTransitionService transitionService;
    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    @Value("${cityhub.order.unpaid-timeout:15m}")
    private Duration unpaidTimeout;

    @Scheduled(cron = "0 * * * * *")
    public void closeExpiredOrders() {
        RLock lock = redissonClient.getLock("lock:job:order-auto-close");
        try {
            if (!lock.tryLock(0, 50, TimeUnit.SECONDS)) {
                return;
            }
            int closed = 0;
            for (int batch = 0; batch < 5; batch++) {
                List<Long> ids = orderMapper.selectExpiredIds(LocalDateTime.now().minus(unpaidTimeout), 200);
                if (ids.isEmpty()) {
                    break;
                }
                for (Long id : ids) {
                    if (transitionService.closeIfUnpaid(id)) {
                        closed++;
                    }
                }
            }
            if (closed > 0) {
                meterRegistry.counter("cityhub.orders.closed").increment(closed);
                log.info("Automatically closed {} expired unpaid orders", closed);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

