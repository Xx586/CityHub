package com.cityhub.seckill;

import com.cityhub.mapper.VoucherOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderDltConsumer {
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;

    static {
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(new ClassPathResource("lua/compensate_seckill.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    private final ObjectMapper objectMapper;
    private final VoucherOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "${cityhub.kafka.order-dlt-topic}", groupId = "cityhub-voucher-order-dlt",
            containerFactory = "orderKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            VoucherOrderEvent event = objectMapper.readValue(record.value(), VoucherOrderEvent.class);
            if (orderMapper.selectById(event.orderId()) != null) {
                redisTemplate.opsForValue().set(SeckillKeys.orderStatus(event.orderId()),
                        "CREATED:" + event.userId(), Duration.ofHours(24));
                meterRegistry.counter("cityhub.kafka.dlt", "result", "already-created").increment();
                return;
            }
            redisTemplate.execute(COMPENSATE_SCRIPT,
                    List.of(SeckillKeys.stock(event.voucherId()), SeckillKeys.orders(event.voucherId()),
                            SeckillKeys.orderStatus(event.orderId())),
                    event.userId().toString(), "FAILED");
            meterRegistry.counter("cityhub.kafka.dlt", "result", "compensated").increment();
            log.error("Kafka order moved to DLT and compensated: eventId={}, orderId={}",
                    event.eventId(), event.orderId());
        } catch (Exception exception) {
            // A DLT handler must not recursively publish back to the same DLT.
            meterRegistry.counter("cityhub.kafka.dlt", "result", "handler-error").increment();
            log.error("DLT message requires manual intervention: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), exception);
        }
    }
}
