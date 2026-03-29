package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPrice;
import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemVendorPriceRepository extends JpaRepository<ItemVendorPrice, Long> {

    List<ItemVendorPrice> findByInventoryItem_InventoryItemIdAndDeletedDateIsNull(int itemId);

    List<ItemVendorPrice> findByInventoryItem_InventoryItemIdAndPriceTypeAndDeletedDateIsNull(
            int itemId, PriceType priceType);

    /** Returns the preferred vendor price for a given item + priceType. */
    Optional<ItemVendorPrice> findByInventoryItem_InventoryItemIdAndPriceTypeAndIsPreferredVendorTrueAndDeletedDateIsNull(
            int itemId, PriceType priceType);

    /** Returns the lowest-priced active entry for a given item + priceType (fallback when no preferred set). */
    @Query("SELECT p FROM ItemVendorPrice p " +
           "WHERE p.inventoryItem.inventoryItemId = :itemId " +
           "AND p.priceType = :priceType " +
           "AND p.deletedDate IS NULL " +
           "ORDER BY p.pricePerUnit ASC")
    List<ItemVendorPrice> findCheapestByItemAndType(
            @Param("itemId") int itemId, @Param("priceType") PriceType priceType);

    List<ItemVendorPrice> findByVendor_IdAndDeletedDateIsNull(int vendorId);

    /** All preferred prices that are currently valid (for dashboard/reporting). */
    @Query("SELECT p FROM ItemVendorPrice p " +
           "WHERE p.isPreferredVendor = true " +
           "AND p.deletedDate IS NULL " +
           "AND (p.validTo IS NULL OR p.validTo >= CURRENT_TIMESTAMP)")
    List<ItemVendorPrice> findAllActivePreferred();

    boolean existsByInventoryItem_InventoryItemIdAndVendor_IdAndPriceTypeAndDeletedDateIsNull(
            int itemId, int vendorId, PriceType priceType);
}
