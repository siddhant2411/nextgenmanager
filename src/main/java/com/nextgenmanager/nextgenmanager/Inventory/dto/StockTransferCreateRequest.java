package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.math.BigDecimal;
import java.util.List;

/** Creates a transfer in DRAFT. Nothing moves until it is dispatched. */
public record StockTransferCreateRequest(
        String fromWarehouseCode,
        String toWarehouseCode,
        String remarks,
        List<Line> lines
) {
    public record Line(int inventoryItemId, BigDecimal quantity, String remarks) {}
}
