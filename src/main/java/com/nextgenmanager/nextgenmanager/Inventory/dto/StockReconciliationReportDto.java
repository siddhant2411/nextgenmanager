package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.util.List;

/**
 * Result of a stock counter reconciliation.
 *
 * <p>The counts matter as much as the rows. An empty {@code rows} list means nothing
 * drifted only if {@code itemsExamined} is greater than zero — otherwise the run was
 * vacuous and proves nothing. Items are skipped when they carry no
 * {@code ProductInventorySettings} (the relation is optional), which is why
 * {@code itemsSkippedNoSettings} is reported rather than swallowed.
 *
 * @param itemsTotal             active items considered
 * @param itemsExamined          items that actually had counters to compare
 * @param itemsSkippedNoSettings items with no inventory settings, so nothing to check
 * @param itemsScalarOnly        examined items with no instance rows — counters are the
 *                               only record of their stock, so drift there is expected
 * @param itemsDrifted           examined items whose counters disagree with their instances
 * @param itemsWarehouseSplitDrifted examined items whose company-wide counters do not equal the
 *                               sum of their per-warehouse rows
 */
public record StockReconciliationReportDto(
        long itemsTotal,
        long itemsExamined,
        long itemsSkippedNoSettings,
        long itemsScalarOnly,
        long itemsDrifted,
        long itemsWarehouseSplitDrifted,
        List<StockReconciliationRowDto> rows
) {
    /** True when the run examined nothing, so a clean result is meaningless. */
    public boolean isVacuous() {
        return itemsExamined == 0;
    }
}
