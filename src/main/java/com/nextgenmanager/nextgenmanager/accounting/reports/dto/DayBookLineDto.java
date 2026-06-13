package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class DayBookLineDto {
    private Long voucherId;
    private String voucherNumber;
    private VoucherType voucherType;
    private LocalDate date;
    private String narration;
    private BigDecimal totalDr;
    private BigDecimal totalCr;
    private List<VoucherLineEntryDto> lines;

    @Data
    public static class VoucherLineEntryDto {
        private String accountCode;
        private String accountName;
        private BigDecimal drAmount;
        private BigDecimal crAmount;
        private String lineNarration;
    }
}
