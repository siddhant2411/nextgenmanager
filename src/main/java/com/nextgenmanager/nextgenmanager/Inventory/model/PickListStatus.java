package com.nextgenmanager.nextgenmanager.Inventory.model;

/** Where a pick has got to. */
public enum PickListStatus {

    /** Being prepared. Not on the floor yet. */
    DRAFT,

    /** Out on the floor to be picked. */
    RELEASED,

    /** Specific units allocated and recorded. Ready for a delivery note to consume. */
    PICKED,

    /** A delivery note has consumed the picked units. The pick is spent. */
    DISPATCHED,

    /** Abandoned. Any allocation is released so the stock is pickable again. */
    CANCELLED
}
