package com.nextgenmanager.nextgenmanager.accounting.tds.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A deductee-wise TDS row — used by the register and the 26Q export. */
@Data
public class TdsEntryDto {
    private Long id;
    private String section;
    private String sectionDescription;
    private String deducteeName;
    private String deducteePan;
    private BigDecimal taxableAmount;
    private BigDecimal tdsAmount;
    private BigDecimal rate;
    private LocalDate deductionDate;
    private String financialYear;
    private String quarter;
    private String status;
    private Long challanId;
}
