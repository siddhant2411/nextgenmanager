package com.nextgenmanager.nextgenmanager.purchase.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a debit note (purchase return) is confirmed. Accounting listens and
 * auto-posts the DEBIT_NOTE voucher (Dr Vendor sub-ledger, Cr Purchases + Input GST).
 */
@Getter
@AllArgsConstructor
public class DebitNoteConfirmedEvent {
    private final Long debitNoteId;
    private final Instant occurredAt = Instant.now();
}
