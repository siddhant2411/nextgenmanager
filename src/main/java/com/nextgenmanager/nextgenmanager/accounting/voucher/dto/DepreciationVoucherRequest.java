package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Periodic depreciation entry. Posts {@code Dr Depreciation (5070E) / Cr Accumulated Depreciation (1020)}.
 */
@Data
public class DepreciationVoucherRequest {

    @NotNull
    private LocalDate date;

    private String narration;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
}
