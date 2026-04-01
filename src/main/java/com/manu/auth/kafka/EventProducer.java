package com.manu.auth.kafka;

import com.manu.auth.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;
    private final String TOPIC = "user-created-event";

    public void publishUserCreatedEvent(UserCreatedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getUserId().toString(), event);
        }
        catch (Exception e) {
            log.error("Exception occurred: KAFKA: {}", e.getMessage());
        }
    }

}
