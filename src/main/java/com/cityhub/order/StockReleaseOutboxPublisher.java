package com.cityhub.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cityhub.domain.entity.OutboxEvent;
import com.cityhub.domain.enums.OutboxStatus;
import com.cityhub.mapper.OutboxEventMapper;
import com.cityhub.seckill.SeckillKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReleaseOutboxPublisher {
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;

    static {
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(new ClassPathResource("lua/compensate_seckill.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 2_000)
    public void publishPending() {
        List<OutboxEvent> events = outboxMapper.selectList(Wrappers.<OutboxEvent>lambdaQuery()
                .eq(OutboxEvent::getStatus, OutboxStatus.PENDING)
                .le(OutboxEvent::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(OutboxEvent::getId)
                .last("LIMIT 50"));
        for (OutboxEvent event : events) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            StockReleaseEvent payload = objectMapper.readValue(event.getPayload(), StockReleaseEvent.class);
            redisTemplate.execute(COMPENSATE_SCRIPT,
                    List.of(SeckillKeys.stock(payload.voucherId()), SeckillKeys.orders(payload.voucherId()),
                            SeckillKeys.orderStatus(payload.orderId())),
                    payload.userId().toString(), "CANCELED");
            event.setStatus(OutboxStatus.DONE);
            outboxMapper.updateById(event);
        } catch (Exception exception) {
            int retries = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(retries, 8))));
            if (retries >= 10) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Stock release outbox exhausted retries, eventId={}", event.getId(), exception);
            } else {
                log.warn("Stock release outbox retry scheduled, eventId={}, attempt={}", event.getId(), retries);
            }
            outboxMapper.updateById(event);
        }
    }
}

