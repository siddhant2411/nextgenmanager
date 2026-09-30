package com.nextgenmanager.nextgenmanager.items.model;

/**
 * Lifecycle of one engineering revision of an {@link InventoryItem}.
 *
 * DRAFT             — engineering fields editable; may be referenced by a draft BOM position,
 *                      but not by a PO, work order, or sales order.
 * PENDING_APPROVAL  — read-only, awaiting release (routed through the generic approval engine).
 * RELEASED          — engineering fields immutable ("locked"). At most one RELEASED revision per
 *                      item is current ({@code InventoryItem.currentRevision}).
 * SUPERSEDED        — was RELEASED, a later revision has taken over as current. Still immutable,
 *                      still referenced by whatever pinned it (BOM history, closed work orders).
 * OBSOLETE          — do not use on anything new; existing references stand.
 */
public enum ItemRevisionStatus {
    DRAFT,
    PENDING_APPROVAL,
    RELEASED,
    SUPERSEDED,
    OBSOLETE
}
