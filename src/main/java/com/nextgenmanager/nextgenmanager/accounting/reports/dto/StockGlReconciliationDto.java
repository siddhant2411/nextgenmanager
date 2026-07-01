package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

/**
 * Perpetual-inventory reconciliation as of a date: each stock GL account (Raw Material / WIP /
 * Finished Goods) against its independent valuation replayed from the InventoryLedger. A non-zero
 * variance means a stock movement did not post to the GL (or vice-versa) and is surfaced for action.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockGlReconciliationDto {
    private LocalDate asOf;
    private List<StockGlReconciliationRowDto> rows;
    private BigDecimal grIrBalance;   // GR/IR Clearing net credit (Cr − Dr) = goods received not invoiced
    private boolean tiesOut;          // all stock rows tie
}
