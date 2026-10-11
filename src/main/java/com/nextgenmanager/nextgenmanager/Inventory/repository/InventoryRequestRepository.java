package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRequestRepository extends JpaRepository<InventoryRequest, Long>,
        JpaSpecificationExecutor<InventoryRequest> {

    java.util.List<InventoryRequest> findBySourceIdAndRequestSource(Long sourceId, InventoryRequestSource requestSource);

    Page<InventoryRequest> findByRequestSourceAndApprovalStatusIn(
            InventoryRequestSource requestSource,
            java.util.List<InventoryApprovalStatus> statuses,
            Pageable pageable
    );

    /**
     * The stores' material request list. A sales order's request is left out unless it is asked
     * for by source: it is that order's stock reservation, raised on approval and consumed by
     * its delivery challan, not something the stores issue against. Approving or rejecting it
     * here would change the order's reservation behind the order's back.
     */
    @Query(value = "SELECT ir.* FROM inventoryRequest ir " +
            "LEFT JOIN inventoryItem ii ON ii.inventoryItemId = ir.inventoryItemRequest " +
            "WHERE (:referenceId IS NULL OR ir.sourceId = :referenceId) " +
            "AND (:source IS NULL OR ir.requestSource = :source) " +
            "AND (:source IS NOT NULL OR ir.requestSource IS DISTINCT FROM 'SALES_ORDER') " +
            "AND (:approvalStatus IS NULL OR ir.approvalStatus = :approvalStatus) " +
            "AND (:itemCode IS NULL OR ii.itemCode::text ILIKE CONCAT('%', :itemCode, '%')) " +
            "AND (:itemName IS NULL OR ii.name::text ILIKE CONCAT('%', :itemName, '%')) " +
            "ORDER BY ir.requestedDate DESC",
            countQuery = "SELECT COUNT(*) FROM inventoryRequest ir " +
                    "LEFT JOIN inventoryItem ii ON ii.inventoryItemId = ir.inventoryItemRequest " +
                    "WHERE (:referenceId IS NULL OR ir.sourceId = :referenceId) " +
                    "AND (:source IS NULL OR ir.requestSource = :source) " +
                    "AND (:source IS NOT NULL OR ir.requestSource IS DISTINCT FROM 'SALES_ORDER') " +
                    "AND (:approvalStatus IS NULL OR ir.approvalStatus = :approvalStatus) " +
                    "AND (:itemCode IS NULL OR ii.itemCode::text ILIKE CONCAT('%', :itemCode, '%')) " +
                    "AND (:itemName IS NULL OR ii.name::text ILIKE CONCAT('%', :itemName, '%'))",
            nativeQuery = true)
    Page<InventoryRequest> searchRequests(
            @Param("itemCode") String itemCode,
            @Param("itemName") String itemName,
            @Param("source") String source,
            @Param("approvalStatus") String approvalStatus,
            @Param("referenceId") Long referenceId,
            Pageable pageable
    );

}
