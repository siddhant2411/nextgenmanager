package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderOperationRepository extends JpaRepository<WorkOrderOperation,Long> {

    List<WorkOrderOperation> findByWorkOrderIdOrderBySequence(int workOrderId);

    boolean existsByWorkOrderAndStatus(
            WorkOrder workOrder,
            OperationStatus status
    );

    WorkOrderOperation findTopByWorkOrderAndSequenceLessThanOrderBySequenceDesc(
            WorkOrder workOrder,
            Integer sequence
    );

    WorkOrderOperation findTopByWorkOrderAndSequenceGreaterThanOrderBySequenceAsc(
            WorkOrder workOrder,
            Integer sequence
    );

    boolean existsByWorkOrderAndStatusNot(
            WorkOrder workOrder,
            OperationStatus status
    );

    List<WorkOrderOperation> findByWorkOrder(WorkOrder workOrder);

    Optional<WorkOrder> findByIdAndDeletedDateIsNull(Long id);
}
