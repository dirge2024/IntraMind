/** Kafka 消息生产者接口，泛型 payload 自动包装为 KafkaMessage 发送。 */
package com.localrag.messaging.contract;

public interface MessageProducer {
    <T> void send(String topic, T payload);
}
