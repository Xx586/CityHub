package com.cityhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record KafkaTopics(String orderTopic, String orderDltTopic) {
    public KafkaTopics(@Value("${cityhub.kafka.order-topic}") String orderTopic,
                       @Value("${cityhub.kafka.order-dlt-topic}") String orderDltTopic) {
        this.orderTopic = orderTopic;
        this.orderDltTopic = orderDltTopic;
    }
}

