package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem,Integer>, JpaSpecificationExecutor<InventoryItem> {

    // This method finds all items where itemCode starts with the given prefix
    List<InventoryItem> findByItemCodeStartingWith(String prefix);

    @Query(value = "SELECT * FROM inventoryItem i WHERE i.deletedDate IS NULL AND (LOWER(CAST(i.name AS text)) LIKE %:search% OR LOWER(CAST(i.itemCode AS text)) LIKE %:search% OR LOWER(CAST(i.hsnCode AS text)) LIKE %:search%)", nativeQuery = true)
    Page<InventoryItem> findAllActiveCategory(@Param("search") String search, Pageable pageable);


    InventoryItem findByInventoryItemIdAndDeletedDateIsNull(int id);

//    @Query(value = "SELECT * FROM find_active_inventory_item_by_id(:idParam)", nativeQuery = true)
//    InventoryItem findByActiveId(@Param("idParam") int id);

    @Query("SELECT i FROM InventoryItem i LEFT JOIN FETCH i.productInventorySettings WHERE i.inventoryItemId = :id AND i.deletedDate IS NULL")
    InventoryItem findByActiveId(@Param("id") int id);


    @Procedure("check_item_code_exists")
    boolean checkItemCodeExists(@Param("itemCodeParam") String itemCode);

    @Query(value = "SELECT * FROM inventoryItem i WHERE " +
            "(LOWER(CAST(i.name AS text)) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(CAST(i.itemCode AS text)) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(CAST(i.hsnCode AS text)) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "i.deletedDate IS NULL",
            countQuery = "SELECT COUNT(*) FROM inventoryItem i WHERE " +
                    "(LOWER(CAST(i.name AS text)) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                    "LOWER(CAST(i.itemCode AS text)) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                    "LOWER(CAST(i.hsnCode AS text)) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
                    "i.deletedDate IS NULL",
            nativeQuery = true)
    Page<InventoryItem> searchActiveInventoryItems(@Param("query") String query, Pageable pageable);

    @Query(value = "SELECT * FROM inventoryItem i WHERE i.deletedDate IS NOT NULL", nativeQuery = true)
    List<InventoryItem> findByDeletedDateIsNotNull();

    @Query(value = "SELECT COUNT(*) FROM inventoryItem i  WHERE  i.deletedDate IS NULL",nativeQuery = true)
    public Object countByDeletedDateIsNull();

    List<InventoryItem> findAllByDeletedDateIsNull();

    List<InventoryItem> findByInventoryItemIdInAndDeletedDateIsNull(List<Integer> ids);
}
