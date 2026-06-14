package com.nextgenmanager.nextgenmanager.common.approval.dto;

import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalStatus;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ApprovalRequestDto {
    private Long id;
    private String documentType;
    private Long documentId;
    private ApprovalStatus status;
    private String requestedBy;
    private Date requestedAt;
    private List<ApprovalStepDto> steps;
}
