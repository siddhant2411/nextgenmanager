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

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM InventoryLedger e " +
           "WHERE (:warehouse IS NULL OR e.warehouse = :warehouse) AND e.quantity > 0")
    double getStockValueByWarehouse(@Param("warehouse") String warehouse);
}
