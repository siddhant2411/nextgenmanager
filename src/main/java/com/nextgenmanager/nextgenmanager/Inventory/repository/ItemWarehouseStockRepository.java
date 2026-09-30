package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.ItemWarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemWarehouseStockRepository extends JpaRepository<ItemWarehouseStock, Long> {

    @Query("SELECT s FROM ItemWarehouseStock s "
            + "WHERE s.inventoryItem.inventoryItemId = :itemId AND s.warehouse.id = :warehouseId")
    Optional<ItemWarehouseStock> find(@Param("itemId") int itemId, @Param("warehouseId") Long warehouseId);

    @Query("SELECT s FROM ItemWarehouseStock s WHERE s.inventoryItem.inventoryItemId = :itemId "
            + "ORDER BY s.warehouse.code")
    List<ItemWarehouseStock> findByItem(@Param("itemId") int itemId);

    @Query("SELECT s FROM ItemWarehouseStock s WHERE s.warehouse.id = :warehouseId "
            + "ORDER BY s.inventoryItem.itemCode")
    List<ItemWarehouseStock> findByWarehouse(@Param("warehouseId") Long warehouseId);

    /**
     * The per-warehouse totals for an item, which must equal the company-wide counters on
     * ProductInventorySettings. Returns zero rather than null when the item has no rows yet.
     */
    @Query("SELECT COALESCE(SUM(s.onHand), 0) FROM ItemWarehouseStock s "
            + "WHERE s.inventoryItem.inventoryItemId = :itemId")
    java.math.BigDecimal sumOnHandForItem(@Param("itemId") int itemId);

    @Query("SELECT COALESCE(SUM(s.reserved), 0) FROM ItemWarehouseStock s "
            + "WHERE s.inventoryItem.inventoryItemId = :itemId")
    java.math.BigDecimal sumReservedForItem(@Param("itemId") int itemId);

    /** Counted into the on-hand side of the invariant: in-transit stock is still owned. */
    @Query("SELECT COALESCE(SUM(s.inTransit), 0) FROM ItemWarehouseStock s "
            + "WHERE s.inventoryItem.inventoryItemId = :itemId")
    java.math.BigDecimal sumInTransitForItem(@Param("itemId") int itemId);
}
