package com.nextgenmanager.nextgenmanager.common.approval.dto;

import com.nextgenmanager.nextgenmanager.common.approval.model.StepStatus;
import lombok.Data;

import java.util.Date;

@Data
public class ApprovalStepDto {
    private Long id;
    private int level;
    private String approverRole;
    private StepStatus status;
    private String actedBy;
    private Date actedAt;
    private String comment;
}
