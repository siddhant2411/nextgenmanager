package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Balance Sheet as at a date, with a comparative date beside it. Income and expense balances not
 * yet closed into Retained Earnings appear as one computed line under equity, so the sheet balances
 * whenever the trial balance does — before or after a year-end close.
 */
@Data
public class BalanceSheetDto {
    private LocalDate asOf;
    private LocalDate previousAsOf;

    private StatementLineDto equity;
    private StatementLineDto liabilities;
    private StatementLineDto assets;

    private BigDecimal totalEquityAndLiabilities;
    private BigDecimal previousTotalEquityAndLiabilities;
    private BigDecimal totalAssets;
    private BigDecimal previousTotalAssets;

    /** Cumulative income − expenses not yet closed into Retained Earnings (already inside {@link #equity}). */
    private BigDecimal unclosedProfit;
    private BigDecimal previousUnclosedProfit;

    /** Assets − (equity + liabilities). Non-zero only when the books themselves don't balance. */
    private BigDecimal difference;
    private BigDecimal previousDifference;
    private boolean balanced;
}
