package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StockGlReconciliationDto;

import java.time.LocalDate;

public interface StockGlReconciliationService {

    /**
     * Reconciles the stock GL accounts (RM/WIP/FG) to the InventoryLedger valuation.
     *
     * @param from  optional cutover/go-live date. When set, both sides are computed over the window
     *              [from, asOf] — excluding pre-migration history so a mid-stream perpetual adoption
     *              can still tie. When null, an all-time view (cumulative GL vs full ledger replay).
     * @param asOf  reporting date.
     */
    StockGlReconciliationDto reconcile(LocalDate from, LocalDate asOf);

    /** Excel rendering of the reconciliation. */
    byte[] toExcel(StockGlReconciliationDto report);
}
