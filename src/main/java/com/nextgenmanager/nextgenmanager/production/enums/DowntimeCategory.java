package com.nextgenmanager.nextgenmanager.production.enums;

/**
 * Categorizes downtime for OEE calculations.
 * PLANNED: Not included in OEE availability loss (e.g., Scheduled Maintenance, Breaks).
 * UNPLANNED: Included in OEE availability loss (e.g., Breakdown, No Operator).
 */
public enum DowntimeCategory {
    PLANNED,
    UNPLANNED
}
