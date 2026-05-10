package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionPriority;

import java.util.Date;
import java.util.List;

/** Allowed in DRAFT approval status only. Non-null fields applied. */
public record PurchaseRequisitionUpdateDto(
        Date requestDate,
        Date requiredByDate,
        String requestedBy,
        String department,
        Integer workCenterId,
        PurchaseRequisitionPriority priority,
        List<PurchaseRequisitionItemCreateDto> items,
        String notes
) {}
