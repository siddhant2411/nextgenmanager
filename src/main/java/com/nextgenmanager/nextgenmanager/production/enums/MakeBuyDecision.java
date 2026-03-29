package com.nextgenmanager.nextgenmanager.production.enums;

/**
 * Outcome of a Make-or-Buy analysis.
 *
 * MAKE            — manufacture in-house; lower cost AND capacity available
 * BUY             — purchase from external supplier; cheaper or capacity constrained
 * SUBCONTRACT     — job-work model: send own materials to a vendor for processing
 *                   (common in Indian MSME under GST Section 143 job-work provisions)
 * INSUFFICIENT_DATA — cannot decide; missing purchase price or standard cost
 */
public enum MakeBuyDecision {
    MAKE,
    BUY,
    SUBCONTRACT,
    INSUFFICIENT_DATA
}
