package com.nextgenmanager.nextgenmanager.quality.model;

/** Where an inspection has got to. PASSED and WAIVED let goods move; the other two do not. */
public enum InspectionLotStatus {

    /** Raised, not yet judged. Stops the movement it gates — silence is not consent. */
    PENDING,

    PASSED,

    FAILED,

    /**
     * Failed, but released anyway by someone who owns that decision. Recorded as its own status
     * rather than as a PASSED lot with a note, so nothing downstream can mistake it for a pass.
     */
    WAIVED
}
