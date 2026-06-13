package com.nextgenmanager.nextgenmanager.accounting.coa.dto;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import lombok.Data;

@Data
public class AccountGroupDto {
    private Long id;
    private String code;
    private String name;
    private Long parentGroupId;
    private String parentGroupName;
    private AccountNature nature;
    private String scheduleIIIHead;
    private int sortOrder;
}
