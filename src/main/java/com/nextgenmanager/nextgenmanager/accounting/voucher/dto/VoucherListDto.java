package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lightweight DTO for list/table views — never touches the lines collection. */
@Data
public class VoucherListDto {
    private Long id;
    private String voucherNumber;
    private VoucherType voucherType;
    private LocalDate date;
    private String narration;
    private VoucherStatus status;
    private BigDecimal totalAmount;
    private String createdBy;
    private String sourceDocType;
    private Long sourceDocId;
}
