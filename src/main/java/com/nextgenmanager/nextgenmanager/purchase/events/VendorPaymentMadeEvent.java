package com.nextgenmanager.nextgenmanager.purchase.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a payment is made to a vendor against a vendor invoice.
 * Accounting listens and auto-posts the PAYMENT voucher (Dr AP sub-ledger, Cr Bank/Cash).
 */
@Getter
@AllArgsConstructor
public class VendorPaymentMadeEvent {
    private final Long vendorPaymentId;
    private final Instant occurredAt = Instant.now();
}
