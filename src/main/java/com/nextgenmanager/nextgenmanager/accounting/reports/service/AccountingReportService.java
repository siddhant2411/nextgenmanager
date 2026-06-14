package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.reports.dto.DayBookDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.LedgerStatementDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.TrialBalanceDto;

import java.time.LocalDate;

public interface AccountingReportService {
    TrialBalanceDto trialBalance(LocalDate asOf);
    DayBookDto dayBook(LocalDate from, LocalDate to);
    LedgerStatementDto ledgerStatement(Long accountId, LocalDate from, LocalDate to);
}
