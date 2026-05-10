package com.nextgenmanager.nextgenmanager.production.repository.workorder;

import com.nextgenmanager.nextgenmanager.production.enums.MaterialIssueStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderMaterialRepository extends JpaRepository<WorkOrderMaterial,Long> {

    @Query("SELECT wm FROM WorkOrderMaterial wm JOIN FETCH wm.component JOIN FETCH wm.workOrder LEFT JOIN FETCH wm.workOrderOperation WHERE wm.id = :id")
    Optional<WorkOrderMaterial> findByIdWithComponent(@Param("id") Long id);

    List<WorkOrderMaterial> findByWorkOrderId(int workOrderId);

    boolean existsByWorkOrderAndIssueStatusNot(
            WorkOrder workOrder,
            MaterialIssueStatus status
    );

    Optional<WorkOrder> findByIdAndDeletedDateIsNull(Long id);

    List<WorkOrderMaterial> findByWorkOrder(WorkOrder workOrder);

    List<WorkOrderMaterial> findByWorkOrderOperationId(Long operationId);

    List<WorkOrderMaterial> findByWorkOrderAndWorkOrderOperationIsNullAndIssueStatusNot(
            WorkOrder workOrder,
            MaterialIssueStatus issueStatus
    );

    List<WorkOrderMaterial> findByWorkOrderAndWorkOrderOperationIsNull(WorkOrder workOrder);

    List<WorkOrderMaterial> findByWorkOrderAndWorkOrderOperation(WorkOrder workOrder, WorkOrderOperation operation);

    Optional<WorkOrderMaterial> findByInventoryRequestId(Long inventoryRequestId);

    @Query("SELECT m FROM WorkOrderMaterial m WHERE m.workOrder = :wo AND m.component.inventoryItemId = :itemId AND m.deletedDate IS NULL")
    List<WorkOrderMaterial> findByWorkOrderAndItemId(@Param("wo") WorkOrder workOrder, @Param("itemId") Long itemId);
}
