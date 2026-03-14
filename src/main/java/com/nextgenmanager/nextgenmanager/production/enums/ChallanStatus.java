package com.nextgenmanager.nextgenmanager.production.enums;

/**
 * Life-cycle of a Job Work Challan (GST Rule 45 / Section 143).
 *
 * DRAFT          — being prepared, materials not yet dispatched
 * DISPATCHED     — materials sent to job worker, 180-day clock started
 * PARTIALLY_RECEIVED — some qty returned, challan still open
 * COMPLETED      — all dispatched qty returned (received or rejected)
 * CANCELLED      — voided before dispatch
 */
public enum ChallanStatus {
    DRAFT,
    DISPATCHED,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CANCELLED
}
