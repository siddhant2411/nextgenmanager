package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/** Column titles exactly as they appear in the statement's header row. */
@Data
public class BankStatementMappingDto {
    private Long ledgerId;
    private boolean saved;
    @Min(1) private int headerRow = 1;
    @NotBlank(message = "Pick the date column") private String dateColumn;
    private String dateFormat;
    private String narrationColumn;
    private String referenceColumn;
    private String withdrawalColumn;
    private String depositColumn;
    private String amountColumn;
    private String balanceColumn;
    private LocalDate reconcileFrom;
}
