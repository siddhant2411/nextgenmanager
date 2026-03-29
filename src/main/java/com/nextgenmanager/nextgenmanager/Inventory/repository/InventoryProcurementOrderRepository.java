package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryProcurementOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryProcurementOrderRepository extends JpaRepository<InventoryProcurementOrder,Long> {


    List<InventoryProcurementOrder> findByInventoryRequestId(Long requestId);

}
