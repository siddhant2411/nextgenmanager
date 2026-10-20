package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.reports.dto.BalanceSheetDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.ProfitLossDto;

import java.time.LocalDate;

public interface FinancialStatementService {

    /** P&L for [from, to] beside [previousFrom, previousTo]; the comparative defaults to the same dates a year earlier. */
    ProfitLossDto profitAndLoss(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo);

    /** Balance Sheet as at asOf beside previousAsOf; the comparative defaults to a year earlier. */
    BalanceSheetDto balanceSheet(LocalDate asOf, LocalDate previousAsOf);

    byte[] profitAndLossExcel(ProfitLossDto report);

    byte[] balanceSheetExcel(BalanceSheetDto report);
}
