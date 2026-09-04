package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.util.List;

/**
 * Asks for named items' stock counters to be reset to what their instance rows say.
 *
 * <p>The item list is deliberately explicit rather than "correct everything that drifted".
 * An item with no instance rows derives to zero, so a blanket correction would wipe the
 * counters of every item whose stock is carried by the counters alone. Naming the items
 * makes that an operator's decision per item, not a side effect.
 */
public record StockScalarCorrectionRequest(
        List<Integer> itemIds,
        String reason
) {}
