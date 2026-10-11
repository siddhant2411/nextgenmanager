package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryBookingApproval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBookingApprovalRepository extends JpaRepository<InventoryBookingApproval,Long> {

    /** The latest decision recorded against a request -- who approved or rejected it, and when. */
    java.util.Optional<InventoryBookingApproval> findFirstByInventoryRequestIdOrderByApprovalDateDesc(Long requestId);
}
