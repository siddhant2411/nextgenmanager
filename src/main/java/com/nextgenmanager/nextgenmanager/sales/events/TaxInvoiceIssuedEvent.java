package com.nextgenmanager.nextgenmanager.sales.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a Tax Invoice is created (and thereby issued — it is born with a
 * legal sequential number). The accounting module listens and auto-posts the SALES
 * voucher. Source modules have zero knowledge of accounting.
 */
@Getter
@AllArgsConstructor
public class TaxInvoiceIssuedEvent {
    private final Long taxInvoiceId;
    private final Instant occurredAt = Instant.now();
}
