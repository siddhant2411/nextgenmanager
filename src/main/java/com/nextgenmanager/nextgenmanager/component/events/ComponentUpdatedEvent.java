package com.nextgenmanager.nextgenmanager.component.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ComponentUpdatedEvent {

    private final Integer componentId;
    private final String fieldChanged;
    private final String oldValue;
    private final String newValue;
    private final Instant occurredAt = Instant.now();

    public ComponentUpdatedEvent(Integer componentId,
                                 String fieldChanged,
                                 String oldValue,
                                 String newValue) {
        this.componentId = componentId;
        this.fieldChanged = fieldChanged;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

}