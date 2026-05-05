package com.localrag.messaging.model;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class KafkaMessage<T> {
    private String messageId;
    private String topic;
    private String timestamp;
    private T payload;

    public static <T> KafkaMessage<T> of(String topic, T payload) {
        KafkaMessage<T> msg = new KafkaMessage<>();
        msg.messageId = UUID.randomUUID().toString();
        msg.topic = topic;
        msg.timestamp = Instant.now().toString();
        msg.payload = payload;
        return msg;
    }
}
