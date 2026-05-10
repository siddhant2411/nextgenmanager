package com.nextgenmanager.nextgenmanager.purchase.dto;

import java.math.BigDecimal;
import java.util.Date;

public record PurchaseOrderItemCreateDto(
        Long itemId,
        Integer lineNumber,
        String description,
        double quantityOrdered,
        BigDecimal unitPrice,
        BigDecimal discountPct,
        /** If null, auto-populated from ProductFinanceSettings.gstRate */
        BigDecimal gstRatePct,
        Date requiredByDate,
        Long salesOrderItemId,
        String remarks
) {}
