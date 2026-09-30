package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.WarehouseType;

/**
 * A warehouse as the API exposes it. Used for both create and update; {@code id} is ignored
 * on create and taken from the path on update.
 */
public record WarehouseDto(
        Long id,
        String code,
        String name,
        WarehouseType warehouseType,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String gstin,
        boolean isDefault,
        boolean binTracked,
        boolean active,
        long locationCount
) {}
