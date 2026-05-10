package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

import java.math.BigDecimal;
import java.util.Date;

public record PurchaseRequisitionItemCreateDto(
        Long itemId,
        Integer lineNumber,
        String description,
        double quantity,
        Date requiredByDate,
        Integer suggestedVendorId,
        BigDecimal estimatedUnitPrice,
        Long workOrderMaterialId,
        String notes
) {}
