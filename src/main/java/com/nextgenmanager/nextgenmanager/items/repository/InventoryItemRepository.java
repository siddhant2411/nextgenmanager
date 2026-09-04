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

    boolean existsByItemCodeAndDeletedDateIsNull(String itemCode);

    /**
     * Active items carrying no inventory settings at all.
     *
     * <p>The settings row is created only when a client sends one, so anything loaded through an
     * import that did not think to include the block has none — and an item without it cannot
     * hold stock, be reserved or be picked, because every one of those paths reads the settings
     * first and gives up when they are null.
     */
    @Query("SELECT i FROM InventoryItem i "
            + "WHERE i.deletedDate IS NULL AND i.productInventorySettings IS NULL "
            + "ORDER BY i.inventoryItemId")
    List<InventoryItem> findActiveWithoutInventorySettings();

    @Query(value = "SELECT * FROM inventoryItem i WHERE i.deletedDate IS NULL AND (LOWER(CAST(i.name AS text)) LIKE %:search% OR LOWER(CAST(i.itemCode AS text)) LIKE %:search% OR LOWER(CAST(i.hsnCode AS text)) LIKE %:search%)", nativeQuery = true)
    Page<InventoryItem> findAllActiveCategory(@Param("search") String search, Pageable pageable);


    InventoryItem findByInventoryItemIdAndDeletedDateIsNull(int id);

//    @Query(value = "SELECT * FROM find_active_inventory_item_by_id(:idParam)", nativeQuery = true)
//    InventoryItem findByActiveId(@Param("idParam") int id);

    @Query("SELECT i FROM InventoryItem i LEFT JOIN FETCH i.productInventorySettings WHERE i.inventoryItemId = :id AND i.deletedDate IS NULL")
    InventoryItem findByActiveId(@Param("id") int id);


    @Procedure("check_item_code_exists")
    boolean checkItemCodeExists(@Param("itemCodeParam") String itemCode);

    @Query("SELECT i FROM InventoryItem i LEFT JOIN FETCH i.productFinanceSettings WHERE " +
            "(LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(i.itemCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(i.hsnCode) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "i.deletedDate IS NULL")
    Page<InventoryItem> searchActiveInventoryItems(@Param("query") String query, Pageable pageable);

    @Query(value = "SELECT * FROM inventoryItem i WHERE i.deletedDate IS NOT NULL", nativeQuery = true)
    List<InventoryItem> findByDeletedDateIsNotNull();

    @Query(value = "SELECT COUNT(*) FROM inventoryItem i  WHERE  i.deletedDate IS NULL",nativeQuery = true)
    public Object countByDeletedDateIsNull();

    List<InventoryItem> findAllByDeletedDateIsNull();

    List<InventoryItem> findByInventoryItemIdInAndDeletedDateIsNull(List<Integer> ids);

    java.util.Optional<InventoryItem> findByItemCodeIgnoreCaseAndDeletedDateIsNull(String itemCode);
}
