package com.nextgenmanager.nextgenmanager.accounting.coa.dto;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AccountGroupCreateDto {
    @NotBlank private String code;
    @NotBlank private String name;
    private Long parentGroupId;
    @NotNull  private AccountNature nature;
    private String scheduleIIIHead;
    private int sortOrder;
}
