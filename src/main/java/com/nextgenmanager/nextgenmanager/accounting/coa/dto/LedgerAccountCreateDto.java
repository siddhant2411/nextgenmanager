package com.nextgenmanager.nextgenmanager.accounting.coa.dto;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LedgerAccountCreateDto {
    @NotBlank private String code;
    @NotBlank private String name;
    @NotNull  private Long groupId;
    @NotNull  private AccountNature nature;
    private BigDecimal openingBalance;
    private String openingBalanceDrCr;
    private boolean isControlAccount;
    private SubLedgerType subLedgerType = SubLedgerType.NONE;
    private Integer contactId;
    private boolean gstApplicable;
    private BigDecimal gstRate;
    private boolean isBankAccount;
    private boolean isCashAccount;
    private boolean isReconcilable;
}
