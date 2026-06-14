package com.nextgenmanager.nextgenmanager.common.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalRuleCreateDto {
    private int sortOrder;
    /** JSON array string: [{"attribute":"amount","operator":">=","value":"50000"}] */
    @NotNull private String conditions;
    @NotBlank private String approverRole;
    @NotNull private Integer level;
    private String description;
}
