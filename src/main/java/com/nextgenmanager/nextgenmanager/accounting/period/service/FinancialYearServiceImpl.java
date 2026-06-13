package com.nextgenmanager.nextgenmanager.accounting.period.service;

import com.nextgenmanager.nextgenmanager.accounting.period.dto.AccountingPeriodDto;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearDto;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FyStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.model.PeriodStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.AccountingPeriodRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.FinancialYearRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.LockedPeriodException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
@RequiredArgsConstructor
public class FinancialYearServiceImpl implements FinancialYearService {

    private final FinancialYearRepository fyRepo;
    private final AccountingPeriodRepository periodRepo;
    private final CompanyDetailsRepository companyDetailsRepo;

    @Override
    public FinancialYearDto create(FinancialYearCreateDto dto) {
        FinancialYear fy = buildAndSaveFinancialYear(dto.getStartYear(), dto.getOpeningDate());
        List<AccountingPeriod> periods = periodRepo.findByFinancialYearIdOrderByPeriodNumber(fy.getId());
        return toDto(fy, periods);
    }

    /** Creates a financial year (from the company's configured start month) plus its 12 monthly periods. */
    private FinancialYear buildAndSaveFinancialYear(int startYear, LocalDate openingDate) {
        int fyStartMonth = configuredFyStartMonth();
        LocalDate startDate = LocalDate.of(startYear, fyStartMonth, 1);
        LocalDate endDate = startDate.plusYears(1).minusDays(1);

        FinancialYear fy = new FinancialYear();
        fy.setLabel("FY " + startYear + "-" + String.valueOf(startYear + 1).substring(2));
        fy.setStartDate(startDate);
        fy.setEndDate(endDate);
        fy.setOpeningDate(openingDate);
        fy.setStatus(FyStatus.ACTIVE);
        fyRepo.save(fy);

        List<AccountingPeriod> periods = new ArrayList<>();
        LocalDate periodStart = startDate;
        for (int i = 1; i <= 12; i++) {
            LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
            AccountingPeriod p = new AccountingPeriod();
            p.setFinancialYear(fy);
            p.setPeriodNumber(i);
            p.setStartDate(periodStart);
            p.setEndDate(periodEnd);
            p.setStatus(PeriodStatus.OPEN);
            periods.add(p);
            periodStart = periodStart.plusMonths(1);
        }
        periodRepo.saveAll(periods);
        return fy;
    }

    private int configuredFyStartMonth() {
        return companyDetailsRepo.findAll().stream().findFirst()
                .map(c -> c.getFinancialYearStartMonth() != null ? c.getFinancialYearStartMonth() : 4)
                .orElse(4);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialYearDto> listAll() {
        return fyRepo.findAll().stream().map(fy -> {
            List<AccountingPeriod> periods = periodRepo.findByFinancialYearIdOrderByPeriodNumber(fy.getId());
            return toDto(fy, periods);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialYearDto getById(Long id) {
        FinancialYear fy = fyRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Financial year not found: " + id));
        List<AccountingPeriod> periods = periodRepo.findByFinancialYearIdOrderByPeriodNumber(id);
        return toDto(fy, periods);
    }

    @Override
    public AccountingPeriodDto lockPeriod(Long periodId) {
        AccountingPeriod p = findPeriod(periodId);
        if (p.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Only OPEN periods can be locked. Current status: " + p.getStatus());
        }
        p.setStatus(PeriodStatus.LOCKED);
        return toPeriodDto(periodRepo.save(p));
    }

    @Override
    public AccountingPeriodDto unlockPeriod(Long periodId) {
        AccountingPeriod p = findPeriod(periodId);
        if (p.getStatus() != PeriodStatus.LOCKED) {
            throw new IllegalStateException("Only LOCKED periods can be unlocked. Current status: " + p.getStatus());
        }
        p.setStatus(PeriodStatus.OPEN);
        return toPeriodDto(periodRepo.save(p));
    }

    @Override
    public AccountingPeriod resolveOpenPeriod(LocalDate date) {
        AccountingPeriod period = periodRepo.findByDate(date)
                .orElseGet(() -> {
                    // No financial year covers this date yet — auto-provision it from the
                    // company's configured FY start month, then resolve the period again.
                    ensureFinancialYearForDate(date);
                    return periodRepo.findByDate(date)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No accounting period could be resolved for date: " + date));
                });
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new LockedPeriodException(date, period.getStatus().name());
        }
        return period;
    }

    /** Lazily creates the financial year covering {@code date} if none exists yet. */
    private void ensureFinancialYearForDate(LocalDate date) {
        if (fyRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date).isPresent()) {
            return;
        }
        int fyStartMonth = configuredFyStartMonth();
        int startYear = date.getMonthValue() >= fyStartMonth ? date.getYear() : date.getYear() - 1;
        buildAndSaveFinancialYear(startYear, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialYear resolveFinancialYear(LocalDate date) {
        return fyRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date)
                .orElseThrow(() -> new IllegalArgumentException("No financial year found for date: " + date));
    }

    private AccountingPeriod findPeriod(Long id) {
        return periodRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Accounting period not found: " + id));
    }

    private FinancialYearDto toDto(FinancialYear fy, List<AccountingPeriod> periods) {
        FinancialYearDto dto = new FinancialYearDto();
        dto.setId(fy.getId());
        dto.setLabel(fy.getLabel());
        dto.setStartDate(fy.getStartDate());
        dto.setEndDate(fy.getEndDate());
        dto.setOpeningDate(fy.getOpeningDate());
        dto.setStatus(fy.getStatus());
        dto.setPeriods(periods.stream().map(this::toPeriodDto).toList());
        return dto;
    }

    AccountingPeriodDto toPeriodDto(AccountingPeriod p) {
        AccountingPeriodDto dto = new AccountingPeriodDto();
        dto.setId(p.getId());
        dto.setPeriodNumber(p.getPeriodNumber());
        dto.setStartDate(p.getStartDate());
        dto.setEndDate(p.getEndDate());
        dto.setStatus(p.getStatus());
        String monthName = Month.of(p.getStartDate().getMonthValue())
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        dto.setPeriodLabel(monthName + " " + p.getStartDate().getYear());
        return dto;
    }
}
