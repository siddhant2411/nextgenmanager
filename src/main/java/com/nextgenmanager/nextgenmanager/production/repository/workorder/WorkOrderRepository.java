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

    /**
     * Work orders raised under {@code parentId}, however the link was made — by hand when
     * creating a sub-assembly order, or automatically by a split. Both write parentWorkOrder,
     * so one query covers them; splitFromWorkOrderId then tells the two apart.
     */
    List<WorkOrder> findByParentWorkOrderIdAndDeletedDateIsNullOrderByIdAsc(int parentId);

    /**
     * Work orders split off {@code sourceId}. Kept separate from the query above so that a split
     * whose parent link was never written — anything created before splits set it — still shows
     * up as related rather than disappearing.
     */
    List<WorkOrder> findBySplitFromWorkOrderIdAndDeletedDateIsNullOrderByIdAsc(int sourceId);
}
