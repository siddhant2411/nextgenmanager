package com.nextgenmanager.nextgenmanager.items.model;

/**
 * Classifies what the vendor price represents.
 *
 * PURCHASE    — vendor supplies the finished/semi-finished item to you.
 *               Used as the BUY cost in Make-or-Buy analysis.
 *
 * JOB_WORK    — vendor processes your raw material and returns the finished item.
 *               You pay only the processing charge (GST Section 143 job-work).
 *               Used as the SUBCONTRACT cost in Make-or-Buy analysis.
 */
public enum PriceType {
    PURCHASE,
    JOB_WORK
}
