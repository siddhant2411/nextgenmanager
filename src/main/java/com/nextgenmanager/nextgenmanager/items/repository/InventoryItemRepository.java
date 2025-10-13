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

    @Query(value = "SELECT * FROM inventoryItem i WHERE i.deletedDate IS NULL AND (LOWER(i.name) LIKE %:search% OR LOWER(i.itemCode) LIKE %:search% OR LOWER(i.hsnCode) LIKE %:search%)", nativeQuery = true)
    Page<InventoryItem> findAllActiveCategory(@Param("search") String search, Pageable pageable);


//    @Query(value = "SELECT * FROM find_active_inventory_item_by_id(:idParam)", nativeQuery = true)
//    InventoryItem findByActiveId(@Param("idParam") int id);

    @Query(value = "SELECT * FROM inventoryItem i WHERE i.inventoryItemId = :id AND i.deletedDate IS NULL", nativeQuery = true)
    InventoryItem findByActiveId(@Param("id") int id);

    @Procedure("check_item_code_exists")
    boolean checkItemCodeExists(@Param("itemCodeParam") String itemCode);

    @Query(value = "SELECT * FROM inventoryItem i WHERE " +
            "(LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(i.itemCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(i.hsnCode) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "i.deletedDate IS NULL",
            countQuery = "SELECT COUNT(*) FROM inventoryItem i WHERE " +
                    "(LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                    "LOWER(i.itemCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                    "LOWER(i.hsnCode) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
                    "i.deletedDate IS NULL",
            nativeQuery = true)
    Page<InventoryItem> searchActiveInventoryItems(@Param("query") String query, Pageable pageable);

    @Query(value = "SELECT * FROM inventoryItem i WHERE i.deletedDate IS NOT NULL", nativeQuery = true)
    List<InventoryItem> findByDeletedDateIsNotNull();

    @Query(value = "SELECT COUNT(*) FROM inventoryItem i  WHERE  i.deletedDate IS NULL",nativeQuery = true)
    public Object countByDeletedDateIsNull();
}
