package com.nextgenmanager.nextgenmanager.common.approval.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApprovalPolicyDto {
    private Long id;
    private String documentType;
    private String description;
    private boolean enabled;
    private List<ApprovalRuleDto> rules;

    @Data
    public static class ApprovalRuleDto {
        private Long id;
        private int sortOrder;
        private String conditions;
        private String approverRole;
        private int level;
        private String description;
    }
}
