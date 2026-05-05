package com.localrag.messaging.contract;

public interface MessageProducer {
    <T> void send(String topic, T payload);
}
