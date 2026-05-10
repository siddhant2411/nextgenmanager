package com.nextgenmanager.nextgenmanager.purchase.model;

public enum PurchaseOrderStatus {
    /** PO is editable, not yet submitted for approval. */
    DRAFT,
    /** Submitted to vendor after approval. */
    SENT,
    /** At least one GRN posted but not all quantities received. */
    PARTIALLY_RECEIVED,
    /** All ordered quantities received. */
    RECEIVED,
    /** Fully invoiced and closed. */
    COMPLETED,
    CANCELLED
}
