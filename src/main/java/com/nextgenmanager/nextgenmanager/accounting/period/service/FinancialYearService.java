package com.nextgenmanager.nextgenmanager.accounting.period.service;

import com.nextgenmanager.nextgenmanager.accounting.period.dto.AccountingPeriodDto;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearDto;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;

import java.time.LocalDate;
import java.util.List;

public interface FinancialYearService {
    FinancialYearDto create(FinancialYearCreateDto dto);
    List<FinancialYearDto> listAll();
    FinancialYearDto getById(Long id);

    AccountingPeriodDto lockPeriod(Long periodId);
    AccountingPeriodDto unlockPeriod(Long periodId);

    /** Returns the OPEN period containing the given date, throws if not found or locked. */
    AccountingPeriod resolveOpenPeriod(LocalDate date);

    /** Returns the financial year containing the given date. */
    FinancialYear resolveFinancialYear(LocalDate date);
}
