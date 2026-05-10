package com.nextgenmanager.nextgenmanager.production.repository.workorder;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderQaEntryRepository extends JpaRepository<WorkOrderQaEntry, Long> {

    List<WorkOrderQaEntry> findByWorkOrderOperationId(Long workOrderOperationId);

    @Query("SELECT e FROM WorkOrderQaEntry e WHERE e.workOrderOperation.workOrder.id = :workOrderId")
    List<WorkOrderQaEntry> findByWorkOrderId(@Param("workOrderId") int workOrderId);
}
