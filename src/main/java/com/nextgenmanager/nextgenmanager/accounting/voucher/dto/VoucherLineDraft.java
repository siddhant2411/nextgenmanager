package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.TaxLineType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VoucherLineDraft {
    @NotNull private Long ledgerAccountId;
    private BigDecimal drAmount = BigDecimal.ZERO;
    private BigDecimal crAmount = BigDecimal.ZERO;
    private String narration;
    private TaxLineType taxType;
    private String hsnSac;
    private BigDecimal taxableValue;
    private BigDecimal taxRate;
}
