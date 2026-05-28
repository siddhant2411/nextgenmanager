package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;

import java.time.LocalDate;
import java.util.List;

/**
 * Manages all stock movements via an immutable ledger.
 *
 * Golden rule — only two events change the physical stock balance:
 *   CONSUME  → decreases stock (reserved → consumed)
 *   PRODUCE  → increases stock (finished goods / purchase receipt / GRN)
 *
 * Everything else is a tracking layer:
 *   RESERVE  → available → reserved   (committed to an order, still in warehouse)
 *   ISSUE    → ledger note only        (physically moved to shop floor, no net change)
 *   RETURN   → reserved/issued → available (put back, e.g. WO cancel / short-close)
 *   ADJUSTMENT → direct correction
 */
public interface InventoryTransactionService {

    /** Commits stock to an order. availableQty -= qty, reservedQty += qty. */
    void reserveStock(InventoryTransactionDTO dto);

    /**
     * Records the physical move from warehouse to shop floor.
     * Does NOT change any balance — stock was already removed from availableQty at RESERVE.
     */
    void issueStock(InventoryTransactionDTO dto);

    /**
     * Consumes reserved stock in production.
     * This is the ONLY operation that reduces total physical stock.
     */
    void consumeStock(InventoryTransactionDTO dto);

    /**
     * Adds finished goods or purchased stock (GRN receipt) to inventory.
     * availableQty += qty. This is the ONLY operation that increases total physical stock.
     */
    void produceStock(InventoryTransactionDTO dto);

    /**
     * Returns committed/issued stock back to available.
     * availableQty += qty, reservedQty -= qty.
     */
    void returnStock(InventoryTransactionDTO dto);

    /** Direct balance correction. Creates an ADJUSTMENT ledger entry. */
    void adjustStock(InventoryTransactionDTO dto);

    /** Returns ledger entries for an item, newest first. */
    List<InventoryLedger> getStockHistory(int itemId, LocalDate from, LocalDate to);

    /**
     * Returns the closing balance of the last ledger entry BEFORE the given date.
     * Returns 0 if no entries exist before that date (i.e. item had no stock yet).
     * Used to show "Opening Balance as on [date]" at the top of a ledger report.
     */
    double getOpeningBalance(int itemId, LocalDate asOf);

    /** Returns the current closing balance for an item (from the most recent ledger entry). */
    double getCurrentStock(int itemId, String warehouse);

    /** Returns the total stock value for a warehouse (sum of GRN receipt amounts still in stock). */
    double getStockValue(String warehouse);

    /**
     * Writes a SALES_DISPATCH ledger entry for goods dispatched via a Delivery Note.
     * Does NOT modify ProductInventorySettings — that is already handled by
     * InventoryInstanceService.updateItemAvailability() when instances are consumed.
     * This is a ledger-only write so the Stock Ledger Report shows the outward movement.
     *
     * @param dto must contain: inventoryItemId, quantity (positive dispatch qty),
     *            transactionType="SALES_DISPATCH", referenceType="DELIVERY_NOTE",
     *            referenceDocNo=dnNumber, createdBy
     */
    void writeDispatchLedger(InventoryTransactionDTO dto);
}
