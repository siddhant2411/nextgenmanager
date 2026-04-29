package com.nextgenmanager.nextgenmanager.production.enums;

public enum WorkOrderStatus {

    CREATED,
    SCHEDULED,
    MATERIAL_PENDING,
    READY_FOR_PRODUCTION,
    PARTIALLY_READY,
    RELEASED,      // deprecated — kept for existing data migration only
    IN_PROGRESS,
    MATERIAL_REORDER,
    READY_FOR_INSPECTION,
    TESTING,
    INSPECTION_FAILED,
    COMPLETED,
    CLOSED,
    CANCELLED,
    SHORT_CLOSED,
    HOLD
}
