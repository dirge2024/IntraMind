/** MessageProducer 的 KafkaTemplate 实现，JSON 序列化 + 异步发送 + 回调日志。 */
package com.localrag.messaging.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localrag.messaging.contract.MessageProducer;
import com.localrag.messaging.model.KafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageProducer implements MessageProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> void send(String topic, T payload) {
        try {
            KafkaMessage<T> message = KafkaMessage.of(topic, payload);
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("kafka send failed: topic={}, md5={}", topic, message.getMessageId(), ex);
                } else {
                    log.info("kafka sent: topic={}, offset={}", topic,
                            result != null ? result.getRecordMetadata().offset() : -1);
                }
            });
        } catch (Exception e) {
            log.error("kafka serialize failed: topic={}", topic, e);
        }
    }
}
