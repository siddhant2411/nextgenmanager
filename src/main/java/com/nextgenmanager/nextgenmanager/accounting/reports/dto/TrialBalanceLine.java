package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor
public class TrialBalanceLine {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private String groupName;
    private AccountNature nature;
    private BigDecimal openingDr;
    private BigDecimal openingCr;
    private BigDecimal movementDr;
    private BigDecimal movementCr;
    private BigDecimal closingDr;
    private BigDecimal closingCr;
}
