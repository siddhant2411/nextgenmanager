package com.nextgenmanager.nextgenmanager.purchase.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a vendor invoice transitions DRAFT → POSTED. Accounting listens and
 * auto-posts the PURCHASE voucher (Dr Purchases + Input GST, Cr Vendor sub-ledger).
 */
@Getter
@AllArgsConstructor
public class VendorInvoicePostedEvent {
    private final Long vendorInvoiceId;
    private final Instant occurredAt = Instant.now();
}
