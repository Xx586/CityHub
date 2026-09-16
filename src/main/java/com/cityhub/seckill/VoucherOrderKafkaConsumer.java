package com.cityhub.seckill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderKafkaConsumer {
    private final ObjectMapper objectMapper;
    private final OrderPersistenceService persistenceService;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "${cityhub.kafka.order-topic}", containerFactory = "orderKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) throws JsonProcessingException {
        VoucherOrderEvent event = objectMapper.readValue(record.value(), VoucherOrderEvent.class);
        persistenceService.createOrderIdempotently(event);
        redisTemplate.opsForValue().set(SeckillKeys.orderStatus(event.orderId()),
                "CREATED:" + event.userId(), Duration.ofHours(24));
        meterRegistry.counter("cityhub.kafka.orders", "result", "created").increment();
        log.info("Voucher order persisted: eventId={}, orderId={}, partition={}, offset={}",
                event.eventId(), event.orderId(), record.partition(), record.offset());
    }
}
