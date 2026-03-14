package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.enums.MaterialIssueStatus;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderMaterialRepository extends JpaRepository<WorkOrderMaterial,Long> {

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
}
