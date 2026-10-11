package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionPriority;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource;

import java.util.Date;
import java.util.List;

public record PurchaseRequisitionCreateDto(
        Date requestDate,
        Date requiredByDate,
        String requestedBy,
        String department,
        Integer workCenterId,
        PurchaseRequisitionPriority priority,
        PurchaseRequisitionSource source,
        Long sourceRefId,
        String sourceRefNumber,
        List<PurchaseRequisitionItemCreateDto> items,
        String notes,
        /** Code of the warehouse the goods are to be received into. Blank means the default warehouse. */
        String deliverToWarehouseCode
) {}
