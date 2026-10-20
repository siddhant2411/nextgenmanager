package com.nextgenmanager.nextgenmanager.accounting.close.service;

import com.nextgenmanager.nextgenmanager.accounting.close.dto.YearEndClosePreviewDto;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearDto;

public interface YearEndCloseService {

    /** The checks and the closing voucher, without changing anything. */
    YearEndClosePreviewDto preview(Long financialYearId);

    /**
     * Posts the CLOSING voucher (income and expenses → Retained Earnings, dated the year's last day)
     * and marks the year and all its months CLOSED. Refused unless every preview check passes.
     */
    FinancialYearDto close(Long financialYearId, String username);

    /**
     * Reverses the closing voucher (never deletes it), puts the months back to LOCKED and the year
     * back to ACTIVE. Refused while a later year is closed.
     */
    FinancialYearDto reopen(Long financialYearId, String reason, String username);
}
