package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterialReorder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkOrderMaterialReorderRepository extends JpaRepository<WorkOrderMaterialReorder, Long> {

    List<WorkOrderMaterialReorder> findByWorkOrderMaterialIdOrderByCreatedDateDesc(Long workOrderMaterialId);

    long countByWorkOrderMaterialId(Long workOrderMaterialId);

    @Query("SELECT womr.inventoryRequestId FROM WorkOrderMaterialReorder womr " +
           "WHERE womr.workOrderMaterial.workOrder.id = :workOrderId " +
           "AND womr.inventoryRequestId IS NOT NULL")
    List<Long> findInventoryRequestIdsByWorkOrderId(@Param("workOrderId") int workOrderId);
}
