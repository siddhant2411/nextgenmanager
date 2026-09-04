package com.nextgenmanager.nextgenmanager.Inventory.dto;

/** One item's stock inside one warehouse. */
public record WarehouseStockRowDto(
        int itemId,
        String itemCode,
        String itemName,
        String warehouseCode,
        double onHand,
        double reserved,
        double inTransit
) {}
