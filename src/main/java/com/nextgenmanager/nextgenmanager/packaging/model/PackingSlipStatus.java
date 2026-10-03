package com.nextgenmanager.nextgenmanager.packaging.model;

/** Where a packing slip has got to. */
public enum PackingSlipStatus {

    /** Being packed. Boxes can still be added or changed. */
    DRAFT,

    /** Every box is recorded. Waiting on any package QC raised against them before it can close. */
    PACKED,

    /** Done. Ready to ship. */
    CLOSED,

    /** Abandoned. The pick behind it is untouched — packing carries no allocation of its own. */
    CANCELLED
}
