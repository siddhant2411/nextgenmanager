package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

/**
 * The order in which a batch of work orders claims capacity. Whatever is sequenced first gets
 * the earliest free slots; later orders fill in around it.
 */
public enum DispatchRule {

    /** Priority first (URGENT before LOW), then the earliest due date. */
    PRIORITY,

    /** Earliest due date first, priority breaking ties. */
    EDD,

    /**
     * Lowest critical ratio first: working time left until the due date divided by the work
     * still to do. Below 1 the order is already behind; overdue orders go first of all.
     */
    CRITICAL_RATIO,

    /**
     * Apparent Tardiness Cost: favours short, high-priority orders while they have slack and
     * lets an order's urgency grow as its slack runs out. Good at keeping total weighted
     * lateness down when the shop is overloaded.
     */
    ATC
}
