package com.nextgenmanager.nextgenmanager.common.events;


public interface DomainEventPublisher {
    void publish(Object event);
}