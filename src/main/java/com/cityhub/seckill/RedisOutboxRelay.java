package com.cityhub.seckill;

import com.cityhub.config.KafkaTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisOutboxRelay {
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopics topics;
    private final String consumerName;

    public RedisOutboxRelay(StringRedisTemplate redisTemplate,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            KafkaTopics topics) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topics = topics;
        this.consumerName = instanceName();
    }

    @PostConstruct
    void createConsumerGroup() {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
                    bytes("CREATE"), bytes(SeckillKeys.OUTBOX_STREAM), bytes(SeckillKeys.OUTBOX_GROUP),
                    bytes("0-0"), bytes("MKSTREAM")));
            log.info("Created Redis outbox consumer group {}", SeckillKeys.OUTBOX_GROUP);
        } catch (DataAccessException exception) {
            if (!String.valueOf(exception.getMessage()).contains("BUSYGROUP")) {
                throw exception;
            }
        }
    }

    @Scheduled(fixedDelay = 500)
    public void relayNewMessages() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(SeckillKeys.OUTBOX_GROUP, consumerName),
                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                    StreamOffset.create(SeckillKeys.OUTBOX_STREAM, ReadOffset.lastConsumed()));
            if (records != null) {
                for (MapRecord<String, Object, Object> record : records) {
                    publishAndAcknowledge(record.getId().getValue(), record.getValue());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Redis outbox relay iteration failed; message remains pending", exception);
        }
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void recoverAbandonedMessages() {
        try {
            for (ClaimedRecord record : autoClaim(Duration.ofSeconds(30), 20)) {
                publishAndAcknowledge(record.id(), record.values());
            }
        } catch (RuntimeException exception) {
            log.warn("Redis outbox pending recovery failed", exception);
        }
    }

    private void publishAndAcknowledge(String recordId, Map<Object, Object> values) {
        VoucherOrderEvent event = toEvent(values);
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topics.orderTopic(), event.orderId().toString(), json).get(10, TimeUnit.SECONDS);
            redisTemplate.opsForStream().acknowledge(
                    SeckillKeys.OUTBOX_STREAM, SeckillKeys.OUTBOX_GROUP, recordId);
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka publish failed for Redis outbox record " + recordId, exception);
        }
    }

    private VoucherOrderEvent toEvent(Map<Object, Object> values) {
        return new VoucherOrderEvent(
                string(values, "eventId"),
                Integer.parseInt(string(values, "eventVersion")),
                Long.valueOf(string(values, "orderId")),
                Long.valueOf(string(values, "userId")),
                Long.valueOf(string(values, "voucherId")),
                Instant.parse(string(values, "createdAt")));
    }

    @SuppressWarnings("unchecked")
    private List<ClaimedRecord> autoClaim(Duration minIdle, int count) {
        Object raw = redisTemplate.execute((RedisCallback<Object>) connection -> connection.execute("XAUTOCLAIM",
                bytes(SeckillKeys.OUTBOX_STREAM), bytes(SeckillKeys.OUTBOX_GROUP), bytes(consumerName),
                bytes(Long.toString(minIdle.toMillis())), bytes("0-0"), bytes("COUNT"), bytes(Integer.toString(count))));
        if (!(raw instanceof List<?> response) || response.size() < 2 || !(response.get(1) instanceof List<?> entries)) {
            return List.of();
        }
        List<ClaimedRecord> claimed = new ArrayList<>();
        for (Object item : entries) {
            if (!(item instanceof List<?> entry) || entry.size() < 2 || !(entry.get(1) instanceof List<?> fields)) {
                continue;
            }
            Map<Object, Object> values = new LinkedHashMap<>();
            for (int i = 0; i + 1 < fields.size(); i += 2) {
                values.put(asString(fields.get(i)), asString(fields.get(i + 1)));
            }
            claimed.add(new ClaimedRecord(asString(entry.get(0)), values));
        }
        return claimed;
    }

    private static String string(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            value = values.get(key.getBytes(StandardCharsets.UTF_8));
        }
        if (value == null) {
            throw new IllegalArgumentException("Missing Redis outbox field: " + key);
        }
        return asString(value);
    }

    private static String asString(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String instanceName() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception exception) {
            return "cityhub-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private record ClaimedRecord(String id, Map<Object, Object> values) {
    }
}
