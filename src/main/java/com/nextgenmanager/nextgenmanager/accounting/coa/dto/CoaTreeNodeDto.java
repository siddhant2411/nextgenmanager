package com.nextgenmanager.nextgenmanager.accounting.coa.dto;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CoaTreeNodeDto {
    private Long id;
    private String code;
    private String name;
    private AccountNature nature;
    private String scheduleIIIHead;
    private int sortOrder;
    private String nodeType; // "GROUP" or "LEDGER"
    private List<CoaTreeNodeDto> children = new ArrayList<>();

    // Ledger-specific (null for group nodes)
    private Boolean isControlAccount;
    private String subLedgerType;
    private Boolean gstApplicable;
}
