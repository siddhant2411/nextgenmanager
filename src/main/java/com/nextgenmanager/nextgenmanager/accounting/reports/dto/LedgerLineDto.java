package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class LedgerLineDto {
    private LocalDate date;
    private String voucherNumber;
    private VoucherType voucherType;
    private String narration;
    private BigDecimal drAmount;
    private BigDecimal crAmount;
    private BigDecimal runningBalance;
    private String runningBalanceDrCr;
}
