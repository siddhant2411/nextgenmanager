package com.nextgenmanager.nextgenmanager.accounting.period.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FinancialYearCreateDto {
    /** The calendar year in which the FY starts. E.g. 2025 → FY 2025-26 (Apr 2025 – Mar 2026). */
    @NotNull
    private Integer startYear;

    /** Optional: cutover date for mid-year go-live. Null = fresh start. */
    private LocalDate openingDate;
}
