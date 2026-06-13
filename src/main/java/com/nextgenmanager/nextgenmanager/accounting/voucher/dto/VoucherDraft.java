package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class VoucherDraft {
    @NotNull  private VoucherType voucherType;
    @NotNull  private LocalDate date;
    private String narration;
    @NotEmpty private List<VoucherLineDraft> lines;

    // Set internally by PostingService for auto-posted documents
    private String sourceDocType;
    private Long sourceDocId;
}
