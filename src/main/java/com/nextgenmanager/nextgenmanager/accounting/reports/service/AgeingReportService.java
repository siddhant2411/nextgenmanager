package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.reports.dto.AgeingReportDto;

import java.time.LocalDate;

public interface AgeingReportService {

    /** Receivables ageing per customer sub-ledger as of the given date (FIFO open-item). */
    AgeingReportDto debtorsAgeing(LocalDate asOf);

    /** Payables ageing per vendor sub-ledger as of the given date (FIFO open-item). */
    AgeingReportDto creditorsAgeing(LocalDate asOf);

    /** Render an ageing report as an .xlsx workbook. */
    byte[] toExcel(AgeingReportDto report);
}
