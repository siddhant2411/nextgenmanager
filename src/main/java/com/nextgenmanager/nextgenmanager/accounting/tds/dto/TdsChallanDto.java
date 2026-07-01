package com.nextgenmanager.nextgenmanager.accounting.tds.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class TdsChallanDto {
    private Long id;
    private String challanNumber;
    private String bsrCode;
    private LocalDate depositDate;
    private BigDecimal amount;
    private String section;
    private String financialYear;
    private String quarter;
    private String notes;
    private int entryCount;

    /** Populated on detail fetch; null in list views. */
    private List<TdsEntryDto> entries;
}
