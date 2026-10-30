package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.SalesReturnGoodsCondition;

public record SalesCreditNoteItemDTO(
        Long id,
        int inventoryItemId,
        String itemCode,
        String itemName,
        int lineNumber,
        double returnedQty,
        double rate,
        double gstRate,
        double gstAmount,
        double totalAmount,
        String warehouseTo,
        String remarks,
        SalesReturnGoodsCondition goodsCondition
) {}
