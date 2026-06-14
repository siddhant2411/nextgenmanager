package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContraVoucherRequest {
    @NotNull private LocalDate date;
    @NotNull private Long fromAccountId;  // Cr (source bank/cash)
    @NotNull private Long toAccountId;    // Dr (destination bank/cash)
    @NotNull private BigDecimal amount;
    private String narration;
}
