package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryMovementLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryMovementLogRepository extends JpaRepository<InventoryMovementLog, Long> {
    List<InventoryMovementLog> findByInventoryInstanceIdOrderByTimestampDesc(Long inventoryInstanceId);
}
