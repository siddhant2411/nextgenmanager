package com.nextgenmanager.nextgenmanager.items.DTO;

import java.util.List;

/**
 * What a backfill of the missing inventory settings did, or would do.
 *
 * <p>Reports every item it touched rather than a count alone: this writes to the item master, and
 * a number on its own gives nobody a way to check the classification was right.
 */
public record InventorySettingsBackfillDto(
        boolean dryRun,
        long itemsWithoutSettings,
        long classifiedPurchased,
        long classifiedManufactured,
        List<Row> rows
) {
    /** One item and the settings it was given. */
    public record Row(
            int itemId,
            String itemCode,
            String itemName,
            boolean purchased,
            boolean manufactured,
            String basis
    ) {}
}
