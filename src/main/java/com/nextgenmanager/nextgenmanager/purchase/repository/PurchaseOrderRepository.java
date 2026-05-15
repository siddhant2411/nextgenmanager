package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findByDeletedDateIsNull(Pageable pageable);

    List<PurchaseOrder> findByStatusInAndDeletedDateIsNull(List<PurchaseOrderStatus> statuses);

    List<PurchaseOrder> findByStatusInAndExpectedDeliveryDateBeforeAndDeletedDateIsNull(
            List<PurchaseOrderStatus> statuses, Date cutoff);

    Page<PurchaseOrder> findByStatusAndDeletedDateIsNull(PurchaseOrderStatus status, Pageable pageable);

    Page<PurchaseOrder> findByApprovalStatusAndDeletedDateIsNull(PurchaseOrderApprovalStatus approvalStatus, Pageable pageable);

    Page<PurchaseOrder> findByVendorIdAndDeletedDateIsNull(Integer vendorId, Pageable pageable);

    Optional<PurchaseOrder> findByIdAndDeletedDateIsNull(Long id);

    boolean existsByPurchaseOrderNumber(String purchaseOrderNumber);

    // ── Analytics ─────────────────────────────────────────────────────────────

    @Query("SELECT p.vendor.companyName, SUM(p.grandTotal) FROM PurchaseOrder p " +
           "WHERE p.deletedDate IS NULL AND p.vendor IS NOT NULL " +
           "GROUP BY p.vendor.companyName ORDER BY SUM(p.grandTotal) DESC")
    List<Object[]> findSpendByVendor(Pageable pageable);

    @Query("SELECT p.status, COUNT(p) FROM PurchaseOrder p WHERE p.deletedDate IS NULL GROUP BY p.status")
    List<Object[]> countByStatus();

    @Query("SELECT EXTRACT(YEAR FROM p.orderDate), EXTRACT(MONTH FROM p.orderDate), SUM(p.grandTotal) " +
           "FROM PurchaseOrder p WHERE p.deletedDate IS NULL AND p.orderDate IS NOT NULL " +
           "GROUP BY EXTRACT(YEAR FROM p.orderDate), EXTRACT(MONTH FROM p.orderDate) " +
           "ORDER BY EXTRACT(YEAR FROM p.orderDate), EXTRACT(MONTH FROM p.orderDate)")
    List<Object[]> findMonthlySpend();
}
