package com.nextgenmanager.nextgenmanager.quality.model;

/** Whether anything is still owed on a non-conformance. */
public enum NcrStatus {

    /** Raised, and nobody has said what to do about it yet. */
    OPEN,

    /** A disposition was decided and carried out. */
    CLOSED
}
