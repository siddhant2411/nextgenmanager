package com.nextgenmanager.nextgenmanager.common.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a source document that may have auto-posted a voucher is cancelled or deleted.
 * Accounting listens and reverses the matching voucher (by sourceDocType + sourceDocId), keeping
 * the GL in step without the source modules knowing accounting exists.
 */
@Getter
@AllArgsConstructor
public class DocumentVoidedEvent {
    private final String sourceDocType;
    private final Long sourceDocId;
    private final String reason;
    private final Instant occurredAt = Instant.now();
}
