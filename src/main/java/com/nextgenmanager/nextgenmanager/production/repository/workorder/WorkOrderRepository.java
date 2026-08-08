package com.nextgenmanager.nextgenmanager.production.repository.workorder;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder,Integer>, JpaSpecificationExecutor<WorkOrder> {
    // WO numbers now come from WorkOrderNumberGenerator (NumberSequence-backed, FY-scoped),
    // matching the SO/PO scheme. The legacy 'workOrderSeq' DB sequence is no longer read.

    List<WorkOrder> findByWorkOrderStatus(WorkOrderStatus status);
}
