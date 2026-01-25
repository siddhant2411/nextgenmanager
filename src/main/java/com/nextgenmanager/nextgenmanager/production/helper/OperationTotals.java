package com.nextgenmanager.nextgenmanager.production.helper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class OperationTotals {
    private BigDecimal totalSetup;
    private BigDecimal totalRun;
    private BigDecimal totalLabour;
    private BigDecimal totalHours;
}