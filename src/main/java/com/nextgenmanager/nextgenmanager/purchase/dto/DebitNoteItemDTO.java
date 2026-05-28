package com.nextgenmanager.nextgenmanager.purchase.dto;

public record DebitNoteItemDTO(
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
        String warehouseFrom,
        String remarks
) {}
