package com.nextgenmanager.nextgenmanager.purchase.dto;

import java.math.BigDecimal;
import java.util.Date;

public record PurchaseOrderItemDto(
        Long id,
        Integer lineNumber,
        Long itemId,
        String itemCode,
        String itemName,
        String hsnCode,
        String description,
        String uom,
        double quantityOrdered,
        double quantityReceived,
        BigDecimal unitPrice,
        BigDecimal discountPct,
        BigDecimal discountAmount,
        BigDecimal taxableValue,
        BigDecimal gstRatePct,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal cessAmount,
        BigDecimal lineTotal,
        Date requiredByDate,
        Long salesOrderItemId,
        String remarks,
        boolean batchTracked,
        boolean serialTracked
) {}
