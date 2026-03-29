package com.nextgenmanager.nextgenmanager.production.helper;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OverheadTotals {
    private BigDecimal overhead;
    private BigDecimal totalCost;
}