package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionPriority;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public record PurchaseRequisitionDto(
        Long id,
        String prNumber,
        Date requestDate,
        Date requiredByDate,
        String requestedBy,
        String department,
        Integer workCenterId,
        String workCenterCode,
        String workCenterName,
        PurchaseRequisitionPriority priority,
        PurchaseRequisitionStatus status,
        PurchaseRequisitionApprovalStatus approvalStatus,
        PurchaseRequisitionSource source,
        Long sourceRefId,
        String sourceRefNumber,
        BigDecimal totalEstimatedAmount,
        String approvedBy,
        Date approvedDate,
        String rejectionReason,
        String notes,
        Date createdDate,
        Date updatedDate,
        List<PurchaseRequisitionItemDto> items
) {}
