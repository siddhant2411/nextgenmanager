package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One stock GL account compared against its independent inventory-ledger valuation. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockGlReconciliationRowDto {
    private String code;
    private String name;
    private BigDecimal stockValue;   // independent value replayed from InventoryLedger
    private BigDecimal glBalance;    // GL account balance (Dr − Cr)
    private BigDecimal variance;     // glBalance − stockValue
    private boolean tiesOut;         // |variance| ≤ ₹1
}
