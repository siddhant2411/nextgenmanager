package com.nextgenmanager.nextgenmanager.accounting.period.dto;

import com.nextgenmanager.nextgenmanager.accounting.period.model.FyStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class FinancialYearDto {
    private Long id;
    private String label;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate openingDate;
    private FyStatus status;
    private List<AccountingPeriodDto> periods;
}
