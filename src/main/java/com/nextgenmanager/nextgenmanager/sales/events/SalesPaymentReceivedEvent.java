package com.nextgenmanager.nextgenmanager.sales.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a customer payment is recorded against a sales order. Accounting
 * listens and auto-posts the RECEIPT voucher (Dr Bank/Cash, Cr Customer sub-ledger).
 */
@Getter
@AllArgsConstructor
public class SalesPaymentReceivedEvent {
    private final Long salesPaymentId;
    private final Instant occurredAt = Instant.now();
}
