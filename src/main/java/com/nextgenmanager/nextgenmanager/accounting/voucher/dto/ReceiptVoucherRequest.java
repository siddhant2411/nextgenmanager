package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReceiptVoucherRequest {
    @NotNull private LocalDate date;
    @NotNull private Long bankOrCashAccountId;   // Dr
    @NotNull private Long fromAccountId;          // Cr (customer sub-ledger or income account)
    @NotNull private BigDecimal amount;
    private String narration;
}
