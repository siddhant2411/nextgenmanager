package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record PickListLineDto(
        Long id,
        int inventoryItemId,
        String itemCode,
        String itemName,
        Long salesOrderItemId,
        String storageLocationCode,
        BigDecimal quantityToPick,
        BigDecimal quantityPicked,
        List<Long> allocatedInstanceIds,
        /**
         * Whether this item is batch or serial tracked. Exposed because the caller has to know
         * before submitting: a tracked line is refused unless it names the instances picked, and
         * the client should say so rather than letting the user find out from a rejected save.
         */
        boolean tracked,
        String remarks
) {}
