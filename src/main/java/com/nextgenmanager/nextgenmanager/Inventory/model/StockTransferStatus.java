package com.nextgenmanager.nextgenmanager.Inventory.model;

/** Where a transfer has got to. Each step moves counters, so the order is load-bearing. */
public enum StockTransferStatus {

    /** Being prepared. Nothing has moved and the lines are still editable. */
    DRAFT,

    /** Left the source. Counted as inTransit there, not yet on hand anywhere. */
    DISPATCHED,

    /** Landed. Any quantity dispatched but never received stays inTransit at the source. */
    RECEIVED,

    /** Abandoned before dispatch. Only reachable from DRAFT, because nothing had moved yet. */
    CANCELLED
}
