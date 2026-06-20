package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, Long> {

    List<InventoryLedger> findByInventoryItem_InventoryItemIdOrderByMovementDateDesc(int inventoryItemId);

    List<InventoryLedger> findByInventoryItem_InventoryItemIdAndMovementDateBetweenOrderByMovementDateDesc(
            int inventoryItemId, LocalDate from, LocalDate to);

    @Query("SELECT e FROM InventoryLedger e WHERE e.inventoryItem.inventoryItemId = :itemId " +
           "AND (:warehouse IS NULL OR e.warehouse = :warehouse) " +
           "ORDER BY e.movementDate DESC")
    List<InventoryLedger> findByItemAndWarehouse(@Param("itemId") int itemId,
                                                  @Param("warehouse") String warehouse);

    @Query("SELECT e FROM InventoryLedger e WHERE e.inventoryItem.inventoryItemId = :itemId " +
           "ORDER BY e.movementDate DESC, e.id DESC")
    List<InventoryLedger> findLatestByItem(@Param("itemId") int itemId,
                                            org.springframework.data.domain.Pageable pageable);

    /**
     * Returns the single most-recent ledger entry STRICTLY BEFORE the given date.
     * Used to derive the opening balance at the start of a date-range report.
     */
    @Query("SELECT e FROM InventoryLedger e WHERE e.inventoryItem.inventoryItemId = :itemId " +
           "AND e.movementDate < :asOf ORDER BY e.movementDate DESC, e.id DESC")
    List<InventoryLedger> findLatestBeforeDate(@Param("itemId") int itemId,
                                               @Param("asOf") LocalDate asOf,
                                               org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM InventoryLedger e " +
           "WHERE (:warehouse IS NULL OR e.warehouse = :warehouse) AND e.quantity > 0")
    double getStockValueByWarehouse(@Param("warehouse") String warehouse);

    /**
     * Cross-item inward/outward register for a date range.
     * type = "INWARD"  → quantity > 0
     * type = "OUTWARD" → quantity < 0
     * type = null      → all movements
     */
    @Query("SELECT e FROM InventoryLedger e " +
           "WHERE e.movementDate BETWEEN :from AND :to " +
           "AND (:type IS NULL " +
           "  OR (:type = 'INWARD'  AND e.quantity > 0) " +
           "  OR (:type = 'OUTWARD' AND e.quantity < 0)) " +
           "ORDER BY e.movementDate DESC, e.id DESC")
    org.springframework.data.domain.Page<InventoryLedger> findRegister(
            @Param("from") java.time.LocalDate from,
            @Param("to")   java.time.LocalDate to,
            @Param("type") String type,
            org.springframework.data.domain.Pageable pageable);

    /**
     * All movements up to asOf reduced to the fields the accounting GL↔stock reconciliation needs:
     * Object[]{transactionType, referenceType, quantity, amount, itemType}. Replayed in-memory against
     * the same posting allowlist the InventoryPostingListener uses, to value stock independently of the GL.
     */
    @Query("SELECT e.transactionType, e.referenceType, e.quantity, e.amount, e.inventoryItem.itemType " +
           "FROM InventoryLedger e WHERE e.movementDate <= :asOf")
    List<Object[]> movementsForReconciliation(@Param("asOf") LocalDate asOf);

    /** Same projection as {@link #movementsForReconciliation}, restricted to a cutover window. */
    @Query("SELECT e.transactionType, e.referenceType, e.quantity, e.amount, e.inventoryItem.itemType " +
           "FROM InventoryLedger e WHERE e.movementDate BETWEEN :from AND :to")
    List<Object[]> movementsForReconciliationInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
