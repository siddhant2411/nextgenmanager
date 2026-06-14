package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Debtors or Creditors ageing as of a date. {@code totals} is the column-wise sum of all rows. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgeingReportDto {
    private String type;            // "DEBTORS" or "CREDITORS"
    private LocalDate asOf;
    private List<AgeingRowDto> rows;
    private AgeingRowDto totals;
}
