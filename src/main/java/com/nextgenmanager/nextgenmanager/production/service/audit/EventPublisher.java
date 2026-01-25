package com.nextgenmanager.nextgenmanager.production.service.audit;

public interface EventPublisher {
    void publish(String eventType, Object payload);
}
