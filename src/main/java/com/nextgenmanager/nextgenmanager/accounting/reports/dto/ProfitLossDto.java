package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Statement of Profit and Loss for a period, with the comparative period beside it (Schedule III
 * asks for previous-year figures). Built from voucher movement in the period, leaving out the
 * year-end CLOSING voucher.
 */
@Data
public class ProfitLossDto {
    private LocalDate from;
    private LocalDate to;
    private LocalDate previousFrom;
    private LocalDate previousTo;

    private StatementLineDto income;
    private StatementLineDto expenses;

    private BigDecimal totalIncome;
    private BigDecimal previousTotalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal previousTotalExpenses;
    /** Income − expenses; negative is a loss. */
    private BigDecimal netProfit;
    private BigDecimal previousNetProfit;
}
