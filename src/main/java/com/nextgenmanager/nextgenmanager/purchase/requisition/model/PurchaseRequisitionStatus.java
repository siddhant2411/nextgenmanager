package com.nextgenmanager.nextgenmanager.purchase.requisition.model;

public enum PurchaseRequisitionStatus {
    /** Editable, not yet submitted for approval. */
    DRAFT,
    /** All lines either converted to PO/RFQ or cancelled. */
    CLOSED,
    CANCELLED
}
