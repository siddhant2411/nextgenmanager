package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsEntryDto;

import java.util.List;

public interface TdsReportService {

    /** Deductee-wise TDS rows for a financial year + quarter. */
    List<TdsEntryDto> register(String financialYear, String quarter);

    /** 26Q deductee-wise data export (Excel). */
    byte[] export26Q(String financialYear, String quarter);
}
