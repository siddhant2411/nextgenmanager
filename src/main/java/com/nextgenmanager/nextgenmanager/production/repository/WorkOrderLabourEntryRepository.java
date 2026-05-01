package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLabourEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkOrderLabourEntryRepository extends JpaRepository<WorkOrderLabourEntry, Long> {

    List<WorkOrderLabourEntry> findByWorkOrderOperationIdAndDeletedDateIsNull(Long operationId);

    @Query("SELECT e FROM WorkOrderLabourEntry e WHERE e.workOrderOperation.workOrder.id = :workOrderId AND e.deletedDate IS NULL ORDER BY e.creationDate ASC")
    List<WorkOrderLabourEntry> findByWorkOrderIdAndDeletedDateIsNull(@Param("workOrderId") Long workOrderId);
}
