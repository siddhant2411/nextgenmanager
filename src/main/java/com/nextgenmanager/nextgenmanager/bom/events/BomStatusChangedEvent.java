package com.nextgenmanager.nextgenmanager.bom.events;

import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class BomStatusChangedEvent {
    private final int bomId;
    private final BomStatus oldStatus;
    private final BomStatus newStatus;
    private final Instant occurredAt = Instant.now();


}