package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Repository
public interface InventoryInstanceRepository extends JpaRepository<InventoryInstance,Long> {

    @Query(value = "SELECT * FROM inventoryInstance i WHERE i.deletedDate IS NULL AND i.inventoryItemRef = :inventoryItemId AND i.quantity>=:qty ORDER BY i.entryDate ASC LIMIT 1", nativeQuery = true)
    public InventoryInstance findLatestInventoryInstance(@Param("inventoryItemId") int inventoryItemId, @Param("qty") double qty);

    @Query(value = "SELECT * FROM inventoryInstance i WHERE i.deletedDate IS NULL AND i.inventoryItemRef = :inventoryItemId ORDER BY i.entryDate DESC LIMIT 1", nativeQuery = true)
    public InventoryInstance findLatestInventoryInstance(@Param("inventoryItemId") int inventoryItemId);


    @Query(value = """
    SELECT COUNT(*)
    FROM inventoryInstance i
    WHERE i.deletedDate IS NULL
      AND i.bookedDate IS NULL
      AND i.inventoryItemRef = :inventoryItemId
      AND i.quantity > 0
      AND i.inventoryInstanceStatus = :status
    """, nativeQuery = true)
    int countAvailableInInventory(@Param("inventoryItemId") int inventoryItemId,
                                  @Param("status") String  status);

    // ── Authoritative stock derivation ────────────────────────────────────────
    // One definition of "available" and "reserved", used by
    // InventoryInstanceService.updateItemAvailability() to recompute BOTH scalars
    // together. Previously available was recomputed two different ways (a
    // status-aware COUNT for NOS items, a status-blind SUM for everything else)
    // and reserved was never recomputed at all, so the pair could not be
    // reconciled once they drifted apart.

    /** Sum of quantity sitting in instances that are free to allocate. */
    @Query(value = """
    SELECT COALESCE(SUM(i.quantity), 0)
    FROM inventoryInstance i
    WHERE i.deletedDate IS NULL
      AND i.bookedDate IS NULL
      AND i.inventoryItemRef = :inventoryItemId
      AND i.quantity > 0
      AND i.inventoryInstanceStatus = 'AVAILABLE'
    """, nativeQuery = true)
    double sumAvailableQuantity(@Param("inventoryItemId") int inventoryItemId);

    /**
     * Sum of quantity spoken for but not yet consumed. REQUESTED is written by
     * InventoryTransactionServiceImpl.reserveStock, BOOKED by
     * InventoryInstanceServiceImp — both mean reserved, so both count here.
     */
    @Query(value = """
    SELECT COALESCE(SUM(i.quantity), 0)
    FROM inventoryInstance i
    WHERE i.deletedDate IS NULL
      AND i.inventoryItemRef = :inventoryItemId
      AND i.quantity > 0
      AND i.inventoryInstanceStatus IN ('REQUESTED', 'BOOKED')
    """, nativeQuery = true)
    double sumReservedQuantity(@Param("inventoryItemId") int inventoryItemId);

    /**
     * Live instance rows for an item. Guards the recount: an item with no instance
     * rows is tracked by the scalars alone (untracked items reserved through
     * InventoryTransactionServiceImpl never get rows), and recomputing it from an
     * empty instance table would silently zero its stock.
     */
    @Query(value = """
    SELECT COUNT(*)
    FROM inventoryInstance i
    WHERE i.deletedDate IS NULL
      AND i.inventoryItemRef = :inventoryItemId
      AND i.quantity > 0
    """, nativeQuery = true)
    long countLiveInstances(@Param("inventoryItemId") int inventoryItemId);

    /**
     * Every instance row an item has ever had, consumed ones included.
     *
     * <p>{@link #countLiveInstances} answers "is there stock", which is not the same question as
     * "is this item tracked by instances at all". Using the live count as the recount guard meant
     * that consuming an item's last unit made the recount skip itself, freezing whatever the
     * counters happened to say — a shipped-out item could keep a reserved quantity for ever.
     */
    @Query(value = """
    SELECT COUNT(*)
    FROM inventoryInstance i
    WHERE i.deletedDate IS NULL
      AND i.inventoryItemRef = :inventoryItemId
    """, nativeQuery = true)
    long countAnyInstances(@Param("inventoryItemId") int inventoryItemId);

    /**
     * Stock of an item that is currently held as failed. Used when a non-conformance decides what
     * becomes of goods rejected at receipt.
     */
    @Query("SELECT i FROM InventoryInstance i "
            + "WHERE i.inventoryItem.inventoryItemId = :itemId "
            + "AND i.qualityStatus = com.nextgenmanager.nextgenmanager.Inventory.model.QualityStatus.FAILED "
            + "AND i.deletedDate IS NULL AND i.quantity > 0")
    List<InventoryInstance> findFailedStock(@Param("itemId") int itemId);

    /**
     * What an item's instances say each warehouse is holding: available and reserved, by
     * warehouse. Used to rebuild the per-warehouse counters when they have drifted.
     *
     * <p>Deliberately says nothing about in-transit — a stock transfer moves counters without
     * touching instances, so instances cannot know about goods on a vehicle.
     *
     * @return rows of [warehouseId, available, reserved]
     */
    @Query(value = """
    SELECT i.warehouse_id,
           COALESCE(SUM(CASE WHEN i.inventoryInstanceStatus = 'AVAILABLE' AND i.bookedDate IS NULL
                             THEN i.quantity ELSE 0 END), 0),
           COALESCE(SUM(CASE WHEN i.inventoryInstanceStatus IN ('REQUESTED', 'BOOKED')
                             THEN i.quantity ELSE 0 END), 0)
    FROM inventoryInstance i
    WHERE i.deletedDate IS NULL
      AND i.inventoryItemRef = :inventoryItemId
      AND i.quantity > 0
      AND i.warehouse_id IS NOT NULL
    GROUP BY i.warehouse_id
    """, nativeQuery = true)
    List<Object[]> sumByWarehouse(@Param("inventoryItemId") int inventoryItemId);

    /** Units allocated to a pick line. Used to release them again when a pick is cancelled. */
    @Query("SELECT i FROM InventoryInstance i WHERE i.pickListLine.id = :lineId AND i.deletedDate IS NULL")
    List<InventoryInstance> findByPickListLineId(@Param("lineId") Long lineId);

    @Query(value = "SELECT * FROM inventoryInstance i WHERE i.inventoryItemRef = :inventoryItemId ORDER BY i.entryDate ASC LIMIT :consumedQty", nativeQuery = true)
    public List<InventoryInstance> getItemsToConsume(@Param("inventoryItemId") int inventoryItemId, @Param("consumedQty") int consumedQty);

//
//    @Transactional
//    @Query(value = "UPDATE inventoryInstance SET bookedDate = :bookedDate WHERE id = :id", nativeQuery = true)
//    public void updateBookedDate(@Param("id") Long id, @Param("bookedDate") Date bookedDate);

    @Query(value = "SELECT * FROM InventoryInstance i  WHERE i.inventoryItemRef = :inventoryItemId AND i.bookedDate IS NULL AND i.isConsumed = false ORDER BY i.entryDate ASC",nativeQuery = true)
    public List<InventoryInstance> getItemsToBook(@Param("inventoryItemId") int inventoryItemId, Pageable pageable);

//    @Query(value = "SELECT " +
//            "   i.inventoryItemRef AS inventoryItemRef, " +
//            "   SUM(i.quantity) AS totalQuantity,COALESCE(AVG(i.costPerUnit), 0) AS averageCost " +
//            "FROM inventoryInstance i " +
//            "INNER JOIN inventoryItem item ON i.inventoryItemRef = item.inventoryItemId " +
//            "WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
//            "AND (:queryCode IS NULL OR LOWER(item.itemCode) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
//            "AND (:queryName IS NULL OR LOWER(item.name) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
//            "AND (:queryHsnCode IS NULL OR LOWER(item.hsnCode) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
//            "AND (:uom IS NULL OR item.uom = :uom)  " +
//            "AND (:itemTypeValue IS NULL OR item.itemType=:itemTypeValue) " +
//            "GROUP BY i.inventoryItemRef " +
//            "HAVING (:filterType IS NULL) " +
//            "   OR (:filterType = '=' AND SUM(i.quantity) = :totalQuantityCondition) " +
//            "   OR (:filterType = '<' AND SUM(i.quantity) < :totalQuantityCondition) " +
//            "   OR (:filterType = '>' AND SUM(i.quantity) > :totalQuantityCondition)",
//            nativeQuery = true)
//    Page<Object[]> getItemsWithTotalQuantity(
//            Pageable pageable,
//            @Param("queryCode") String itemCode,
//            @Param("queryName") String itemName,
//            @Param("queryHsnCode") String hsnCode,
//            @Param("totalQuantityCondition") Double totalQuantityCondition,
//            @Param("filterType") String filterType,
//            @Param("uom") Integer uom,
//            @Param("itemTypeValue") Integer itemTypeValue);

//    Page<InventoryInstance> findByInventoryItem(int inventoryItemId, Pageable pageable);

//    @Query(value = "SELECT * FROM inventoryInstance i where i.id=:inventoryItemId AND i.deletedDate IS NULL", nativeQuery = true)
//    InventoryInstance findByItemId(long inventoryItemId);

    @Query(value = "WITH inventory_summary AS ( " +
            "   SELECT " +
            "       i.inventoryItemRef AS inventoryItemRef, " +
            "       item.itemCode AS itemCode, " +
            "       item.name AS name, " +
            "       item.hsnCode AS hsnCode, " +
            "       item.itemType AS itemType, " +
            "       item.uom AS uom, " +
            "       SUM(i.quantity) AS totalQuantity, " +
            "       SUM(i.costPerUnit * i.quantity) AS totalCost " +
            "   FROM inventoryInstance i " +
            "   INNER JOIN inventoryItem item ON i.inventoryItemRef = item.inventoryItemId " +
            "   WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
            "   AND (:queryCode IS NULL OR LOWER(CAST(item.itemCode AS text)) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
            "   AND (:queryName IS NULL OR LOWER(CAST(item.name AS text)) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
            "   AND (:queryHsnCode IS NULL OR LOWER(CAST(item.hsnCode AS text)) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
            "   AND (:uom IS NULL OR item.uom = :uom) " +
            "   AND (:itemTypeValue IS NULL OR item.itemType = :itemTypeValue) " +
            "   GROUP BY i.inventoryItemRef, item.itemCode, item.name, item.hsnCode, item.itemType, item.uom " +
            "   HAVING (:filterType IS NULL) " +
            "       OR (:filterType = '=' AND SUM(i.quantity) = :totalQuantityCondition) " +
            "       OR (:filterType = '<' AND SUM(i.quantity) < :totalQuantityCondition) " +
            "       OR (:filterType = '>' AND SUM(i.quantity) > :totalQuantityCondition) " +
            ") " +
            "SELECT * FROM inventory_summary",
            nativeQuery = true)
    Page<Object[]> getItemsForInventoryPage(
            Pageable pageable,
            @Param("queryCode") String itemCode,
            @Param("queryName") String itemName,
            @Param("queryHsnCode") String hsnCode,
            @Param("totalQuantityCondition") Double totalQuantityCondition,
            @Param("filterType") String filterType,
            @Param("uom") Integer uom,
            @Param("itemTypeValue") Integer itemTypeValue);


//    @Query(value = "WITH inventory_summary AS ( " +
//            "   SELECT * "+
//            "   FROM inventoryInstance i " +
//            "   INNER JOIN inventoryItem item ON i.inventoryItemRef = item.inventoryItemId " +
//            "   WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
//            "   AND (:queryCode IS NULL OR LOWER(item.itemCode) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
//            "   AND (:queryName IS NULL OR LOWER(item.name) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
//            "   AND (:queryHsnCode IS NULL OR LOWER(item.hsnCode) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
//            "   AND (:uom IS NULL OR item.uom = :uom) " +
//            "   AND (:itemTypeValue IS NULL OR item.itemType = :itemTypeValue) " +
//            "   HAVING (:filterType IS NULL) " +
//            "       OR (:filterType = '=' AND SUM(i.quantity) = :totalQuantityCondition) " +
//            "       OR (:filterType = '<' AND SUM(i.quantity) < :totalQuantityCondition) " +
//            "       OR (:filterType = '>' AND SUM(i.quantity) > :totalQuantityCondition) " +
//            ") " +
//            "SELECT * FROM inventory_summary",
//            nativeQuery = true)
//    Page<InventoryInstance> getActiveInstances(
//            Pageable pageable,
//            @Param("queryCode") String itemCode,
//            @Param("queryName") String itemName,
//            @Param("queryHsnCode") String hsnCode,
//            @Param("totalQuantityCondition") Double totalQuantityCondition,
//            @Param("filterType") String filterType,
//            @Param("uom") Integer uom,
//            @Param("itemTypeValue") Integer itemTypeValue);

    @Query(value = "SELECT i.* FROM inventoryInstance i " +
            "JOIN inventoryItem item ON item.InventoryItemId = i.inventoryItemRef " +
            "WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
            "  AND (:queryCode IS NULL OR LOWER(CAST(item.itemCode AS text)) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
            "  AND (:queryName IS NULL OR LOWER(CAST(item.name AS text)) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
            "  AND (:queryHsnCode IS NULL OR LOWER(CAST(item.hsnCode AS text)) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
            "  AND (:uom IS NULL OR item.uom = :uom) " +
            "  AND (:itemTypeValue IS NULL OR item.itemType = :itemTypeValue) ",
            nativeQuery = true)
    List<InventoryInstance> getAllActiveInstancesFiltered(
            @Param("queryCode") String itemCode,
            @Param("queryName") String itemName,
            @Param("queryHsnCode") String hsnCode,
            @Param("uom") Integer uom,
            @Param("itemTypeValue") Integer itemTypeValue
    );

    @Query(value = "SELECT COUNT(*) FROM InventoryInstance i WHERE i.deletedDate IS NULL AND (:status IS NULL OR i.inventoryInstanceStatus = :status)", nativeQuery = true)
    Long countByInventoryInstanceStatus(@Param("status") String status);

    @Query("SELECT SUM(i.quantity) FROM InventoryInstance i " +
           "WHERE i.inventoryInstanceStatus = 'AVAILABLE' " +
           "AND i.deletedDate IS NULL")
    BigDecimal sumAvailableQuantity();

    @Query("SELECT COALESCE(SUM(i.costPerUnit * i.quantity), 0) FROM InventoryInstance i " +
            "WHERE i.deletedDate IS NULL " +
            "AND i.inventoryInstanceStatus <> 'CONSUMED'")
    BigDecimal countSumOfInventoryValue();

    @Query(value = "SELECT * FROM InventoryInstance i WHERE i.deletedDate IS NULL AND i.inventoryItemRef = :inventoryItemId AND (:status IS NULL OR i.inventoryInstanceStatus = :status) LIMIT :qty", nativeQuery = true)
    List<InventoryInstance> inventoryInstanceByStatus(@Param("inventoryItemId") int inventoryItemId, String status, double qty);

    /** FIFO selection of instances in a given status — used for CONSUME and RETURN operations. */
    @Query(value = "SELECT * FROM inventoryInstance i WHERE i.inventoryItemRef = :itemId AND i.inventoryInstanceStatus = :status AND i.deletedDate IS NULL ORDER BY i.entryDate ASC", nativeQuery = true)
    List<InventoryInstance> findByItemAndStatusFIFO(@Param("itemId") int itemId, @Param("status") String status);

//    @Query("""
//    SELECT i FROM InventoryInstance i
//    WHERE i.inventoryInstanceStatus = :status
//      AND (:itemCode IS NULL OR i.inventoryItem.itemCode LIKE %:itemCode%)
//      AND (:itemName IS NULL OR i.inventoryItem.name LIKE %:itemName%)
//      AND (:source IS NULL OR i.requestSource = :source)
//      AND (:approvalStatus IS NULL OR i.approvalStatus = :approvalStatus)
//      AND (:referenceId IS NULL OR i.linkedSourceId = :referenceId)
//""")
//    List<InventoryInstance> findAllByStatusAndFilters(
//            @Param("status") InventoryInstanceStatus status,
//            @Param("itemCode") String itemCode,
//            @Param("itemName") String itemName,
//            @Param("source") InventoryRequestSource source,
//            @Param("approvalStatus") InventoryApprovalStatus approvalStatus,
//            @Param("referenceId") Long referenceId
//    );
//
//    @Query("SELECT i FROM InventoryInstance i " +
//            "WHERE i.inventoryInstanceStatus = :status " +
//            "AND i.linkedSourceId = :referenceId " +
//            "AND i.inventoryItem.InventoryItemId = :inventoryItemId")
//    List<InventoryInstance> findByStatusAndReferenceIdAndItemId(
//            @Param("status") InventoryInstanceStatus status,
//            @Param("referenceId") Long referenceId,
//            @Param("inventoryItemId") int inventoryItemId
//    );

}