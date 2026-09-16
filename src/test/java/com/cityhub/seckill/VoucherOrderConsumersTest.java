package com.cityhub.seckill;

import com.cityhub.mapper.VoucherOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherOrderConsumersTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    VoucherOrderConsumersTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void mainConsumerPersistsThenMarksCreated() throws Exception {
        OrderPersistenceService persistence = mock(OrderPersistenceService.class);
        VoucherOrderKafkaConsumer consumer = new VoucherOrderKafkaConsumer(
                objectMapper, persistence, redis, new SimpleMeterRegistry());

        consumer.consume(record());

        verify(persistence).createOrderIdempotently(any(VoucherOrderEvent.class));
        verify(values).set(eq("seckill:order:status:1001"), eq("CREATED:7"),
                any(java.time.Duration.class));
    }

    @Test
    void dltDoesNotCompensateWhenOrderWasActuallyCommitted() {
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        when(orders.selectById(1001L)).thenReturn(new com.cityhub.domain.entity.VoucherOrder());
        VoucherOrderDltConsumer consumer = new VoucherOrderDltConsumer(
                objectMapper, orders, redis, new SimpleMeterRegistry());

        consumer.consume(record());

        verify(values).set(eq("seckill:order:status:1001"), eq("CREATED:7"),
                any(java.time.Duration.class));
        verify(redis, never()).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dltCompensatesRedisOnlyWhenDatabaseOrderIsAbsent() {
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        when(orders.selectById(1001L)).thenReturn(null);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(Object[].class))).thenReturn(1L);
        VoucherOrderDltConsumer consumer = new VoucherOrderDltConsumer(
                objectMapper, orders, redis, new SimpleMeterRegistry());

        consumer.consume(record());

        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                eq(java.util.List.of("seckill:stock:1", "seckill:orders:1", "seckill:order:status:1001")),
                eq("7"), eq("FAILED"));
    }

    private ConsumerRecord<String, String> record() {
        String json = """
                {"eventId":"event-1","eventVersion":1,"orderId":1001,"userId":7,
                 "voucherId":1,"createdAt":"2026-09-16T07:00:00Z"}
                """;
        return new ConsumerRecord<>("cityhub.voucher-order.created.v1", 0, 10L, "1001", json);
    }
}
