package com.nextgenmanager.nextgenmanager.Inventory.dto;

/** A bin within a warehouse. */
public record StorageLocationDto(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String code,
        String aisle,
        String rack,
        String bin,
        boolean pickable,
        boolean active
) {}
