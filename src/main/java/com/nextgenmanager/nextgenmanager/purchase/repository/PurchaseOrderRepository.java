package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStatusAndDeletedDateIsNull(PurchaseOrderStatus status);
    List<PurchaseOrder> findByDeletedDateIsNull();
}
