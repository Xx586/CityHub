package com.cityhub.seckill;

import com.cityhub.config.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOutboxRelayTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final RedisOutboxRelay relay = new RedisOutboxRelay(redis, kafka,
            new ObjectMapper().findAndRegisterModules(),
            new KafkaTopics("cityhub.voucher-order.created.v1", "cityhub.voucher-order.created.v1.dlt"));

    RedisOutboxRelayTest() {
        when(redis.opsForStream()).thenReturn(streams);
    }

    @Test
    void acknowledgesRedisOnlyAfterKafkaBrokerSuccess() {
        MapRecord<String, Object, Object> record = record("1-0");
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of(record));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relayNewMessages();

        verify(kafka).send(anyString(), anyString(), anyString());
        verify(streams).acknowledge(SeckillKeys.OUTBOX_STREAM, SeckillKeys.OUTBOX_GROUP, "1-0");
    }

    @Test
    void kafkaFailureLeavesRecordPendingForRecovery() {
        MapRecord<String, Object, Object> record = record("2-0");
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of(record));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        relay.relayNewMessages();

        verify(streams, never()).acknowledge(anyString(), anyString(), anyString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MapRecord<String, Object, Object> record(String id) {
        Map<Object, Object> values = Map.of(
                "eventId", "event-1",
                "eventVersion", "1",
                "orderId", "1001",
                "userId", "7",
                "voucherId", "1",
                "createdAt", "2026-09-16T07:00:00Z");
        return (MapRecord) MapRecord.create(SeckillKeys.OUTBOX_STREAM, values).withId(RecordId.of(id));
    }
}
