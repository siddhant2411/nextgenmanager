package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full detail DTO — includes lines. Only used for single-voucher fetch (JOIN FETCH used in repo). */
@Data
public class VoucherDto {
    private Long id;
    private String voucherNumber;
    private VoucherType voucherType;
    private LocalDate date;
    private String narration;
    private VoucherStatus status;
    private String createdBy;
    private Long periodId;
    private String sourceDocType;
    private Long sourceDocId;
    private Long reversalOfId;
    private Long reversedByVoucherId;
    private List<VoucherLineDto> lines;
    private BigDecimal totalDr;
    private BigDecimal totalCr;
}
