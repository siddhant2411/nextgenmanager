package com.nextgenmanager.nextgenmanager.sales.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a sales credit note is confirmed. Accounting listens and auto-posts the
 * CREDIT_NOTE voucher (Dr Sales + Output GST, Cr Customer sub-ledger) — reversing the
 * original sales invoice.
 */
@Getter
@AllArgsConstructor
public class SalesCreditNoteConfirmedEvent {
    private final Long salesCreditNoteId;
    private final Instant occurredAt = Instant.now();
}
