package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryPresentDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface InventoryInstanceRepository extends JpaRepository<InventoryInstance,Long> {

    @Query(value = "SELECT * FROM inventoryInstance i WHERE i.deletedDate IS NULL AND i.inventoryItemRef = :inventoryItemId AND i.quantity>=:qty ORDER BY i.entryDate ASC LIMIT 1", nativeQuery = true)
    public InventoryInstance findLatestInventoryInstance(@Param("inventoryItemId") int inventoryItemId, @Param("qty") double qty);

    @Query(value = "SELECT * FROM inventoryInstance i WHERE i.deletedDate IS NULL AND i.inventoryItemRef = :inventoryItemId ORDER BY i.entryDate DESC LIMIT 1", nativeQuery = true)
    public InventoryInstance findLatestInventoryInstance(@Param("inventoryItemId") int inventoryItemId);


    @Query(value = "SELECT COUNT(*) FROM inventoryInstance i WHERE i.deletedDate IS NULL AND i.bookedDate is NULL AND i.inventoryItemRef = :inventoryItemId AND i.quantity>0", nativeQuery = true)
    public int countAvailableInInventory(@Param("inventoryItemId") int inventoryItemId);


    @Query(value = "SELECT * FROM inventoryInstance i WHERE i.inventoryItemRef = :inventoryItemId ORDER BY i.entryDate ASC LIMIT :consumedQty", nativeQuery = true)
    public List<InventoryInstance> getItemsToConsume(@Param("inventoryItemId") int inventoryItemId, @Param("consumedQty") int consumedQty);


    @Transactional
    @Query(value = "UPDATE inventoryInstance SET bookedDate = :bookedDate WHERE id = :id", nativeQuery = true)
    public void updateBookedDate(@Param("id") Long id, @Param("bookedDate") Date bookedDate);

    @Query(value = "SELECT * FROM InventoryInstance i  WHERE i.inventoryItemRef = :inventoryItemId AND i.bookedDate IS NULL AND i.isConsumed = false ORDER BY i.entryDate ASC",nativeQuery = true)
    public List<InventoryInstance> getItemsToBook(@Param("inventoryItemId") int inventoryItemId, Pageable pageable);

    @Query(value = "SELECT " +
            "   i.inventoryItemRef AS inventoryItemRef, " +
            "   SUM(i.quantity) AS totalQuantity,COALESCE(AVG(i.costPerUnit), 0) AS averageCost " +
            "FROM inventoryInstance i " +
            "INNER JOIN inventoryItem item ON i.inventoryItemRef = item.inventoryItemId " +
            "WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
            "AND (:queryCode IS NULL OR LOWER(item.itemCode) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
            "AND (:queryName IS NULL OR LOWER(item.name) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
            "AND (:queryHsnCode IS NULL OR LOWER(item.hsnCode) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
            "AND (:uom IS NULL OR item.uom = :uom)  " +
            "AND (:itemTypeValue IS NULL OR item.itemType=:itemTypeValue) " +
            "GROUP BY i.inventoryItemRef " +
            "HAVING (:filterType IS NULL) " +
            "   OR (:filterType = '=' AND SUM(i.quantity) = :totalQuantityCondition) " +
            "   OR (:filterType = '<' AND SUM(i.quantity) < :totalQuantityCondition) " +
            "   OR (:filterType = '>' AND SUM(i.quantity) > :totalQuantityCondition)",
            nativeQuery = true)
    Page<Object[]> getItemsWithTotalQuantity(
            Pageable pageable,
            @Param("queryCode") String itemCode,
            @Param("queryName") String itemName,
            @Param("queryHsnCode") String hsnCode,
            @Param("totalQuantityCondition") Double totalQuantityCondition,
            @Param("filterType") String filterType,
            @Param("uom") Integer uom,
            @Param("itemTypeValue") Integer itemTypeValue);

    Page<InventoryInstance> findByInventoryItem(int inventoryItemId, Pageable pageable);

    @Query(value = "SELECT * FROM inventoryInstance i where i.id=:inventoryItemId AND i.deletedDate IS NULL", nativeQuery = true)
    InventoryInstance findByItemId(long inventoryItemId);

    @Query(value = "WITH inventory_summary AS ( " +
            "   SELECT " +
            "       i.inventoryItemRef AS inventoryItemRef, " +
            "       item.itemCode AS itemCode, " +
            "       item.name AS name, " +
            "       item.hsnCode AS hsnCode, " +
            "       item.itemType AS itemType, " +
            "       item.uom AS uom, " +
            "       SUM(i.quantity) AS totalQuantity, " +
            "       SUM(i.costPerUnit) AS totalCost " +
            "   FROM inventoryInstance i " +
            "   INNER JOIN inventoryItem item ON i.inventoryItemRef = item.inventoryItemId " +
            "   WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
            "   AND (:queryCode IS NULL OR LOWER(item.itemCode) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
            "   OR (:queryName IS NULL OR LOWER(item.name) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
            "   AND (:queryHsnCode IS NULL OR LOWER(item.hsnCode) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
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


    @Query(value = "WITH inventory_summary AS ( " +
            "   SELECT * "+
            "   FROM inventoryInstance i " +
            "   INNER JOIN inventoryItem item ON i.inventoryItemRef = item.inventoryItemId " +
            "   WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
            "   AND (:queryCode IS NULL OR LOWER(item.itemCode) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
            "   AND (:queryName IS NULL OR LOWER(item.name) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
            "   AND (:queryHsnCode IS NULL OR LOWER(item.hsnCode) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
            "   AND (:uom IS NULL OR item.uom = :uom) " +
            "   AND (:itemTypeValue IS NULL OR item.itemType = :itemTypeValue) " +
            "   HAVING (:filterType IS NULL) " +
            "       OR (:filterType = '=' AND SUM(i.quantity) = :totalQuantityCondition) " +
            "       OR (:filterType = '<' AND SUM(i.quantity) < :totalQuantityCondition) " +
            "       OR (:filterType = '>' AND SUM(i.quantity) > :totalQuantityCondition) " +
            ") " +
            "SELECT * FROM inventory_summary",
            nativeQuery = true)
    Page<InventoryInstance> getActiveInstances(
            Pageable pageable,
            @Param("queryCode") String itemCode,
            @Param("queryName") String itemName,
            @Param("queryHsnCode") String hsnCode,
            @Param("totalQuantityCondition") Double totalQuantityCondition,
            @Param("filterType") String filterType,
            @Param("uom") Integer uom,
            @Param("itemTypeValue") Integer itemTypeValue);

    @Query(value = "SELECT COALESCE(SUM(i.quantity), 0) FROM inventoryInstance i WHERE i.deletedDate IS NULL AND bookedDate IS NULL AND i.inventoryItemRef = :inventoryItemId", nativeQuery = true)
    public  double getTotalQuantityForNonNOSItem(@Param("inventoryItemId") int inventoryItemId);

    @Query(value = "SELECT i.* FROM inventoryInstance i " +
            "JOIN inventoryItem item ON item.InventoryItemId = i.inventoryItemRef " +
            "WHERE i.deletedDate IS NULL AND i.quantity > 0 " +
            "  AND (:queryCode IS NULL OR LOWER(item.itemCode::text) LIKE LOWER(CONCAT('%', :queryCode, '%'))) " +
            "  AND (:queryName IS NULL OR LOWER(item.name::text) LIKE LOWER(CONCAT('%', :queryName, '%'))) " +
            "  AND (:queryHsnCode IS NULL OR LOWER(item.hsnCode::text) LIKE LOWER(CONCAT('%', :queryHsnCode, '%'))) " +
            "  AND (:uom IS NULL OR item.uom = :uom) " +
            "  AND (:itemTypeValue IS NULL OR item.itemType = :itemTypeValue) " +
            "  AND (:approvalStatus IS NULL OR i.approvalStatus = :approvalStatus) " +
            "  AND (:procurementDecision IS NULL OR i.procurementDecision = :procurementDecision)",
            nativeQuery = true)
    List<InventoryInstance> getAllActiveInstancesFiltered(
            @Param("queryCode") String itemCode,
            @Param("queryName") String itemName,
            @Param("queryHsnCode") String hsnCode,
            @Param("uom") Integer uom,
            @Param("itemTypeValue") Integer itemTypeValue,
            @Param("approvalStatus") String approvalStatus,
            @Param("procurementDecision") String procurementDecision
    );

    @Query(value = "SELECT COUNT(*) FROM InventoryInstance i  WHERE  i.deletedDate IS NULL AND (:status IS NULL OR i.inventoryInstanceStatus = :status)",nativeQuery = true)
    public Object countByInventoryInstanceStatus(InventoryInstanceStatus status);

    @Query("SELECT SUM(i.costPerUnit) FROM InventoryInstance i " +
            "WHERE i.deletedDate IS NULL " +
            "AND (:status IS NULL OR i.inventoryInstanceStatus <> :status)")
    Double countSumOfInventoryValue(InventoryInstanceStatus status);

    @Query(value = "SELECT * FROM InventoryInstance i  WHERE  i.deletedDate IS NULL AND (:status IS NULL OR i.inventoryInstanceStatus <> :status)  LIMIT  :qty",nativeQuery = true)
    public List<InventoryInstance> inventoryInstanceByStatus(InventoryInstanceStatus status, double qty);

}