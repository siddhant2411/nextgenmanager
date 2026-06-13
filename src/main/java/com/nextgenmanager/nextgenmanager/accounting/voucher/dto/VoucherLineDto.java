package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.TaxLineType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VoucherLineDto {
    private Long id;
    private Long ledgerAccountId;
    private String ledgerAccountCode;
    private String ledgerAccountName;
    private BigDecimal drAmount;
    private BigDecimal crAmount;
    private String narration;
    private TaxLineType taxType;
    private String hsnSac;
    private BigDecimal taxableValue;
    private BigDecimal taxRate;
}
