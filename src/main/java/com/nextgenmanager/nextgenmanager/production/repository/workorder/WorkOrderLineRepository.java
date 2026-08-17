package com.nextgenmanager.nextgenmanager.production.repository.workorder;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WorkOrderLineRepository extends JpaRepository<WorkOrderLine, Long> {

    List<WorkOrderLine> findByWorkOrderOrderByLineNumberAsc(WorkOrder workOrder);

    List<WorkOrderLine> findByWorkOrderIdOrderByLineNumberAsc(int workOrderId);

    Optional<WorkOrderLine> findByWorkOrderIdAndLineNumber(int workOrderId, Integer lineNumber);

    /** Highest line number currently used on a work order; empty when it has no lines. */
    @Query("SELECT MAX(l.lineNumber) FROM WorkOrderLine l WHERE l.workOrder.id = :workOrderId")
    Optional<Integer> findMaxLineNumber(int workOrderId);

    long countByWorkOrderId(int workOrderId);
}
