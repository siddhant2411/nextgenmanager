package com.nextgenmanager.nextgenmanager.accounting.tds.model;

/** Lifecycle of a single TDS deduction: deducted at payment, then deposited via challan. */
public enum TdsEntryStatus {
    DEDUCTED,
    DEPOSITED
}
