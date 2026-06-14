package com.nextgenmanager.nextgenmanager.accounting.coa.dto;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LedgerAccountDto {
    private Long id;
    private String code;
    private String name;
    private Long groupId;
    private String groupName;
    private AccountNature nature;
    private BigDecimal openingBalance;
    private String openingBalanceDrCr;
    private boolean isControlAccount;
    private SubLedgerType subLedgerType;
    private Integer contactId;
    private String contactName;
    private boolean gstApplicable;
    private BigDecimal gstRate;
    private boolean isBankAccount;
    private boolean isCashAccount;
    private boolean isReconcilable;
    private boolean isSystem;
}
