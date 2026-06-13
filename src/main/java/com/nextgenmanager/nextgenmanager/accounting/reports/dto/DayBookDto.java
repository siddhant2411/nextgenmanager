package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class DayBookDto {
    private LocalDate from;
    private LocalDate to;
    private List<DayBookLineDto> vouchers;
    private BigDecimal grandTotalDr;
    private BigDecimal grandTotalCr;
}
