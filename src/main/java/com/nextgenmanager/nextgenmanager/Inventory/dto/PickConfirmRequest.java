package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Records what was actually picked.
 *
 * <p>A line left out is treated as picked in full for untracked items. Batch- and serial-tracked
 * items must name their instances: which specific unit left the shelf is the entire point of
 * tracking them.
 */
public record PickConfirmRequest(
        String pickedBy,
        String remarks,
        List<Line> lines
) {
    public record Line(Long lineId, BigDecimal quantityPicked, List<Long> instanceIds) {}
}
