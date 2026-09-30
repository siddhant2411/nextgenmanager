package com.nextgenmanager.nextgenmanager.quality.model;

/** What was decided about goods that failed. */
public enum NcrDisposition {

    /** Put right and inspected again. The stock stays, but not as good stock. */
    REWORK,

    /** Written off. The goods leave the books. */
    SCRAP,

    /**
     * Shipped or used despite the failure. Needs a name against it — this is the disposition that
     * overrides an inspection rather than acting on it.
     */
    USE_AS_IS,

    /** Sent back where it came from. */
    RETURN_TO_VENDOR
}
