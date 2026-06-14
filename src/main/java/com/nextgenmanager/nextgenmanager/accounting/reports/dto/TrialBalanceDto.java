package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class TrialBalanceDto {
    private LocalDate asOf;
    private List<TrialBalanceLine> lines;
    private BigDecimal totalDr;
    private BigDecimal totalCr;
    private boolean balanced;
}
