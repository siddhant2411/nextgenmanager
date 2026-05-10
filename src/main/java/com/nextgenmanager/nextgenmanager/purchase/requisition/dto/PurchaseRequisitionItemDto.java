package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItemStatus;

import java.math.BigDecimal;
import java.util.Date;

public record PurchaseRequisitionItemDto(
        Long id,
        Integer lineNumber,
        Long itemId,
        String itemCode,
        String itemName,
        String description,
        String uom,
        double quantity,
        Date requiredByDate,
        Integer suggestedVendorId,
        String suggestedVendorName,
        BigDecimal estimatedUnitPrice,
        BigDecimal estimatedAmount,
        PurchaseRequisitionItemStatus lineStatus,
        Long workOrderMaterialId,
        Long purchaseOrderId,
        String notes
) {}
