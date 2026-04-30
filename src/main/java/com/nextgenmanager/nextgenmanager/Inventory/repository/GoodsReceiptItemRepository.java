package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, Long> {
    List<GoodsReceiptItem> findByGoodsReceiptNote_Id(Long grnId);

    @Query("SELECT MAX(g.goodsReceiptNote.grnDate) FROM GoodsReceiptItem g " +
           "WHERE g.item.inventoryItemId = :itemId " +
           "AND g.goodsReceiptNote.vendor.id = :vendorId")
    LocalDate findLastPurchasedDate(@Param("itemId") int itemId, @Param("vendorId") int vendorId);
}
