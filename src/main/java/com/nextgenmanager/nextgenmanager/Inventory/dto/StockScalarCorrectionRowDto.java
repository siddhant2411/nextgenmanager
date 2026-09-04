package com.nextgenmanager.nextgenmanager.Inventory.dto;

/** Before-and-after for one item whose stock counters were reset to its instance state. */
public record StockScalarCorrectionRowDto(
        int itemId,
        String itemCode,
        double previousAvailable,
        double newAvailable,
        double previousReserved,
        double newReserved,
        boolean applied,
        String message
) {}
