package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class LedgerStatementDto {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal openingBalance;
    private String openingBalanceDrCr;
    private List<LedgerLineDto> lines;
    private BigDecimal closingBalance;
    private String closingBalanceDrCr;
}
