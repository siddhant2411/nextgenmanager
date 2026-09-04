package com.nextgenmanager.nextgenmanager.Inventory.dto;

/**
 * Builds a pick for whatever on a sales order is not already on another live pick.
 * Blank warehouse means the default.
 */
public record PickListCreateRequest(
        Long salesOrderId,
        String warehouseCode,
        String remarks
) {}
