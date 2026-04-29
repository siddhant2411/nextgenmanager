package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryProcurementOrder;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryProcurementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryProcurementOrderRepository extends JpaRepository<InventoryProcurementOrder, Long> {

    List<InventoryProcurementOrder> findByInventoryRequestId(Long requestId);

    @Query(value = "SELECT ipo.* FROM inventoryProcurementOrder ipo " +
            "LEFT JOIN inventoryItem ii ON ii.inventoryItemId = ipo.inventoryItemProcurementRequest " +
            "WHERE (:status IS NULL OR ipo.inventoryProcurementStatus = :status) " +
            "AND (:inventoryItemId IS NULL OR ipo.inventoryItemProcurementRequest = :inventoryItemId) " +
            "AND (:createdBy IS NULL OR ipo.createdBy::text ILIKE CONCAT('%', :createdBy, '%')) " +
            "AND (:itemCode IS NULL OR ii.itemCode::text ILIKE CONCAT('%', :itemCode, '%')) " +
            "ORDER BY ipo.creationDate DESC",
            countQuery = "SELECT COUNT(*) FROM inventoryProcurementOrder ipo " +
                    "LEFT JOIN inventoryItem ii ON ii.inventoryItemId = ipo.inventoryItemProcurementRequest " +
                    "WHERE (:status IS NULL OR ipo.inventoryProcurementStatus = :status) " +
                    "AND (:inventoryItemId IS NULL OR ipo.inventoryItemProcurementRequest = :inventoryItemId) " +
                    "AND (:createdBy IS NULL OR ipo.createdBy::text ILIKE CONCAT('%', :createdBy, '%')) " +
                    "AND (:itemCode IS NULL OR ii.itemCode::text ILIKE CONCAT('%', :itemCode, '%'))",
            nativeQuery = true)
    Page<InventoryProcurementOrder> searchOrders(
            @Param("status") String status,
            @Param("inventoryItemId") Long inventoryItemId,
            @Param("createdBy") String createdBy,
            @Param("itemCode") String itemCode,
            Pageable pageable
    );
}
