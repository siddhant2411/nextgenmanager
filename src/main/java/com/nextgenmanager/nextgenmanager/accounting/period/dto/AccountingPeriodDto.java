package com.nextgenmanager.nextgenmanager.accounting.period.dto;

import com.nextgenmanager.nextgenmanager.accounting.period.model.PeriodStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AccountingPeriodDto {
    private Long id;
    private int periodNumber;
    private String periodLabel;
    private LocalDate startDate;
    private LocalDate endDate;
    private PeriodStatus status;
}
