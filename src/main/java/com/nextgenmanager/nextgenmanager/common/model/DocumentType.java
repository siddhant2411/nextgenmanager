package com.nextgenmanager.nextgenmanager.common.model;

/**
 * Canonical document types used across Purchase, Accounts, and other modules.
 * Tier drives UI behaviour: ESSENTIAL items warn on close, RECOMMENDED are informational.
 */
public enum DocumentType {

    // ── Purchase ─────────────────────────────────────────────────────────────
    VENDOR_QUOTATION    (Tier.ESSENTIAL,    "Vendor Quotation"),
    PURCHASE_ORDER      (Tier.ESSENTIAL,    "Purchase Order"),
    ORDER_ACKNOWLEDGMENT(Tier.ESSENTIAL,    "Order Acknowledgment (OA)"),
    DELIVERY_CHALLAN    (Tier.ESSENTIAL,    "Delivery Challan"),
    INVOICE             (Tier.ESSENTIAL,    "Invoice"),
    GRN                 (Tier.ESSENTIAL,    "Goods Receipt Note (GRN)"),
    EWAY_BILL           (Tier.RECOMMENDED,  "E-Way Bill"),
    PACKING_LIST        (Tier.RECOMMENDED,  "Packing List"),
    TEST_CERTIFICATE    (Tier.RECOMMENDED,  "Test Certificate / MTC"),
    REJECTION_NOTE      (Tier.RECOMMENDED,  "Rejection Note"),

    // ── Accounts (ready for future use) ──────────────────────────────────────
    VENDOR_BILL         (Tier.ESSENTIAL,    "Vendor Bill"),
    PAYMENT_VOUCHER     (Tier.ESSENTIAL,    "Payment Voucher"),
    DEBIT_NOTE          (Tier.RECOMMENDED,  "Debit Note"),
    CREDIT_NOTE         (Tier.RECOMMENDED,  "Credit Note"),
    TDS_CERTIFICATE     (Tier.RECOMMENDED,  "TDS Certificate"),

    // ── User-defined ──────────────────────────────────────────────────────────
    CUSTOM              (Tier.RECOMMENDED,  "Custom");

    public enum Tier { ESSENTIAL, RECOMMENDED }

    public final Tier tier;
    public final String label;

    DocumentType(Tier tier, String label) {
        this.tier  = tier;
        this.label = label;
    }
}
