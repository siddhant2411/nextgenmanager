package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class JournalVoucherRequest {
    @NotNull  private LocalDate date;
    private String narration;
    @NotEmpty private List<VoucherLineDraft> lines;
}
