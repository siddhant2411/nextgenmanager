package com.nextgenmanager.nextgenmanager.bom.events;

import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class BomModifiedEvent {

    private final int bomId;
    private final int version;
    private final String revision;
    private final BomStatus status;
    private final Instant occurredAt = Instant.now();

    public BomModifiedEvent(int bomId, int version, String revision, BomStatus status) {
        this.bomId = bomId;
        this.version = version;
        this.revision = revision;
        this.status = status;
    }


}