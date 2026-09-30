package com.nextgenmanager.nextgenmanager.Inventory.dto;

/**
 * One item's stock counters compared against what its instance rows actually say.
 *
 * <p>Two subsystems write these counters: {@code InventoryTransactionServiceImpl}
 * adjusts them arithmetically on every movement, while
 * {@code InventoryInstanceServiceImp.updateItemAvailability()} recomputes them from
 * instance state. Where the two disagree, this row shows by how much.
 *
 * <p>{@code scalarOnly} items have no instance rows at all — untracked stock reserved
 * through the transaction service never produces any. Their counters are the only
 * record of stock, so a non-zero drift there is expected rather than a defect, and the
 * recount deliberately leaves them alone.
 */
public record StockReconciliationRowDto(
        int itemId,
        String itemCode,
        String itemName,
        double scalarAvailable,
        double derivedAvailable,
        double availableDrift,
        double scalarReserved,
        double derivedReserved,
        double reservedDrift,
        long liveInstances,
        boolean scalarOnly,
        double warehouseOnHand,
        double warehouseInTransit,
        double warehouseReserved,
        double warehouseOnHandDrift,
        double warehouseReservedDrift
) {
    /** Counters disagree with the instance rows behind them. */
    public boolean hasDrift() {
        return Math.abs(availableDrift) > 0.0001 || Math.abs(reservedDrift) > 0.0001;
    }

    /**
     * The company-wide counters disagree with the sum of this item's per-warehouse rows.
     *
     * <p>Unlike {@link #hasDrift()} this holds for every item, tracked or not — an item with no
     * instances still has to add up across warehouses. It is the check the original pair of
     * counters never had.
     *
     * <p>In-transit counts towards the on-hand side: stock on a vehicle has left one warehouse
     * and not reached the next, but it is still owned, so excluding it would make the invariant
     * false for the whole duration of every transfer.
     */
    public boolean hasWarehouseSplitDrift() {
        return Math.abs(warehouseOnHandDrift) > 0.0001 || Math.abs(warehouseReservedDrift) > 0.0001;
    }
}
