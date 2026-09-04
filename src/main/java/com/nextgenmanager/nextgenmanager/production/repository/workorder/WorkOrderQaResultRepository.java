package com.nextgenmanager.nextgenmanager.production.repository.workorder;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkOrderQaResultRepository extends JpaRepository<WorkOrderQaResult, Long> {

    /**
     * Critical parameters that were measured and failed, anywhere on a work order.
     *
     * <p>These have always been recorded and never acted on: the order completed and the finished
     * goods went on the shelf regardless. The quality gate reads this.
     */
    @Query("SELECT r FROM WorkOrderQaResult r "
            + "JOIN r.workOrderQaEntry e "
            + "JOIN e.workOrderOperation o "
            + "WHERE o.workOrder.id = :workOrderId "
            + "AND e.critical = true "
            + "AND r.result = com.nextgenmanager.nextgenmanager.production.enums.QaResult.FAIL")
    List<WorkOrderQaResult> findFailedCriticalForWorkOrder(@Param("workOrderId") int workOrderId);
}
