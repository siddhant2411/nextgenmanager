package com.nextgenmanager.nextgenmanager.common.events;

import com.nextgenmanager.nextgenmanager.common.security.CurrentUser;
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
    /**
     * Who cancelled the document, taken when the event is raised — the reversal runs after the
     * commit, and the audit trail must name the person, not "SYSTEM". Null for work no one signed in to.
     */
    private final String voidedBy = CurrentUser.nameOrNull();
}
