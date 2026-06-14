package com.nextgenmanager.nextgenmanager.sales.dto;

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
        String remarks
) {}
