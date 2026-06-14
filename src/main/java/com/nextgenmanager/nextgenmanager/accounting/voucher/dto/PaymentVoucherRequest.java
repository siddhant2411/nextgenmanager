package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentVoucherRequest {
    @NotNull private LocalDate date;
    @NotNull private Long toAccountId;            // Dr (vendor sub-ledger or expense account)
    @NotNull private Long bankOrCashAccountId;    // Cr
    @NotNull private BigDecimal amount;
    private String narration;
}
