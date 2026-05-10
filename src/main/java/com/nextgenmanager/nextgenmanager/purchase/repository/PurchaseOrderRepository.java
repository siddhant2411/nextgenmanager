package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findByDeletedDateIsNull(Pageable pageable);

    Page<PurchaseOrder> findByStatusAndDeletedDateIsNull(PurchaseOrderStatus status, Pageable pageable);

    Page<PurchaseOrder> findByApprovalStatusAndDeletedDateIsNull(PurchaseOrderApprovalStatus approvalStatus, Pageable pageable);

    Page<PurchaseOrder> findByVendorIdAndDeletedDateIsNull(Integer vendorId, Pageable pageable);

    Optional<PurchaseOrder> findByIdAndDeletedDateIsNull(Long id);

    boolean existsByPurchaseOrderNumber(String purchaseOrderNumber);
}
