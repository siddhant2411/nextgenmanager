package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.Inventory.model.GRNStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoodsReceiptNoteRepository extends JpaRepository<GoodsReceiptNote, Long> {

    Optional<GoodsReceiptNote> findByGrnNumber(String grnNumber);

    List<GoodsReceiptNote> findByPurchaseOrder_Id(Long purchaseOrderId);

    @Query("SELECT g FROM GoodsReceiptNote g WHERE " +
           "(:poId IS NULL OR g.purchaseOrder.id = :poId) AND " +
           "(:status IS NULL OR g.status = :status) AND " +
           "(:vendorId IS NULL OR g.vendor.id = :vendorId) AND " +
           "(:grnNumber IS NULL OR LOWER(g.grnNumber) LIKE LOWER(CONCAT('%', :grnNumber, '%')))" +
           " ORDER BY g.grnDate DESC, g.id DESC")
    Page<GoodsReceiptNote> search(@Param("poId") Long poId,
                                   @Param("status") GRNStatus status,
                                   @Param("vendorId") Long vendorId,
                                   @Param("grnNumber") String grnNumber,
                                   Pageable pageable);
}
