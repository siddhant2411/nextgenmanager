package com.nextgenmanager.nextgenmanager.packaging.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One item packed into a box. {@code pickListLineId} ties it back to what was picked; naming
 * {@code instanceIds} is required when the item is batch or serial tracked, the same rule the pick
 * confirm step applies.
 */
public record PackageLineRequest(
        Long pickListLineId,
        Integer inventoryItemId,
        BigDecimal quantity,
        List<Long> instanceIds
) {}
