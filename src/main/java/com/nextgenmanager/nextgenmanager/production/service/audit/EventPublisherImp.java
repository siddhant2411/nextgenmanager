package com.nextgenmanager.nextgenmanager.production.service.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventPublisherImp implements EventPublisher{
    private static final Logger log = LoggerFactory.getLogger(EventPublisherImp.class);

    @Override
    public void publish(String eventType, Object payload) {
        log.info("EVENT PUBLISHED: {} | Payload: {}", eventType, payload);
        // Extend with Kafka/RabbitMQ/Webhooks later
    }
}
