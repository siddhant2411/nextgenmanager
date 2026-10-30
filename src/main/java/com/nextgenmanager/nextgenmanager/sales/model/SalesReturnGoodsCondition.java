package com.nextgenmanager.nextgenmanager.sales.model;

/** What a credit note line does with the goods the customer sent back. */
public enum SalesReturnGoodsCondition {

    /** Fit to sell again: back into the chosen warehouse as ordinary stock. */
    GOOD,

    /**
     * Came back faulty: held on the books as FAILED, in the quarantine warehouse when there is one,
     * so it cannot be picked until somebody decides what to do with it.
     */
    DEFECTIVE,

    /** Nothing physical came back — a rate difference or a short supply. Stock is not touched. */
    NOT_RETURNED
}
