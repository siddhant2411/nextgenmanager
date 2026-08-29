package com.nextgenmanager.nextgenmanager.marketing.enquiry.model;

/**
 * Groups close reasons by what actually happened commercially, so the pipeline can be
 * reported on without parsing free text.
 *
 * WON            — the enquiry became an order.
 * LOST           — we quoted and the customer went elsewhere (price, competitor).
 * NO_ENGAGEMENT  — the customer went silent; nothing was ever decided either way.
 * DECLINED_BY_US — we chose not to pursue it (out of scope, qty too low, no vendor price).
 * DEFERRED       — the customer's project is on hold; may come back.
 * INVALID        — not a real enquiry.
 *
 * The distinction that matters: LOST is a competitive defeat and belongs in a win-rate
 * denominator; DECLINED_BY_US and NO_ENGAGEMENT do not, and lumping them together is
 * what makes a hand-kept register useless for measuring anything.
 */
public enum EnquiryCloseOutcome {
    WON,
    LOST,
    NO_ENGAGEMENT,
    DECLINED_BY_US,
    DEFERRED,
    INVALID
}
