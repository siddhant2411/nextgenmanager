package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionPriority;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionStatus;

import java.math.BigDecimal;
import java.util.Date;

public record PurchaseRequisitionListDto(
        Long id,
        String prNumber,
        Date requestDate,
        Date requiredByDate,
        String requestedBy,
        String department,
        PurchaseRequisitionPriority priority,
        PurchaseRequisitionStatus status,
        PurchaseRequisitionApprovalStatus approvalStatus,
        PurchaseRequisitionSource source,
        BigDecimal totalEstimatedAmount,
        int itemCount,
        Date createdDate
) {}
