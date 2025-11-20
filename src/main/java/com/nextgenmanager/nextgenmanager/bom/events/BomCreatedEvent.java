package com.nextgenmanager.nextgenmanager.bom.events;

import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
public class BomCreatedEvent {
    private final int bomId;
    private final int parentItemId;
    private final int versionNumber;
    private final String revision;
    private final BomStatus status;
    private final Instant occurredAt = Instant.now();

    public BomCreatedEvent(int bomId, int parentItemId,
                           int versionNumber, String revision,
                           BomStatus status) {
        this.bomId = bomId;
        this.parentItemId = parentItemId;
        this.versionNumber = versionNumber;
        this.revision = revision;
        this.status = status;
    }

}
