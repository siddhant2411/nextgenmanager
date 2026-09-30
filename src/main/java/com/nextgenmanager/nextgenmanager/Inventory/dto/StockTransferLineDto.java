package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.math.BigDecimal;

public record StockTransferLineDto(
        Long id,
        int inventoryItemId,
        String itemCode,
        String itemName,
        BigDecimal quantity,
        BigDecimal receivedQuantity,
        String remarks
) {}
