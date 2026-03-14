package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WorkOrderOperationRepository extends JpaRepository<WorkOrderOperation,Long> {

    List<WorkOrderOperation> findByWorkOrderIdOrderBySequence(int workOrderId);

    @Query("SELECT op FROM WorkOrderOperation op " +
           "LEFT JOIN FETCH op.workCenter " +
           "LEFT JOIN FETCH op.routingOperation " +
           "LEFT JOIN FETCH op.assignedMachine " +
           "WHERE op.workOrder.id = :workOrderId " +
           "ORDER BY op.sequence")
    List<WorkOrderOperation> findByWorkOrderIdWithAssociationsOrderBySequence(@Param("workOrderId") int workOrderId);

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

    // ── Parallel operation dependency queries ──

    /**
     * Find all WorkOrderOperations that have {@code opId} in their dependsOnOperationIds set.
     * Used after an operation completes to find which operations it unblocks.
     */
    @Query("SELECT op FROM WorkOrderOperation op JOIN op.dependsOnOperationIds depId " +
           "WHERE depId = :opId AND op.deletedDate IS NULL")
    List<WorkOrderOperation> findByDependsOnOperationId(@Param("opId") Long opId);

    /**
     * Count operations for a work order whose status is within the given set.
     * Used to track how many operations are currently IN_PROGRESS or READY.
     */
    @Query("SELECT COUNT(op) FROM WorkOrderOperation op " +
           "WHERE op.workOrder = :workOrder AND op.status IN :statuses AND op.deletedDate IS NULL")
    long countByWorkOrderAndStatusIn(@Param("workOrder") WorkOrder workOrder,
                                     @Param("statuses") Set<OperationStatus> statuses);

    // ── Machine-level scheduling queries ──

    List<WorkOrderOperation> findByAssignedMachineIdAndPlannedStartDateBetweenOrderByPlannedStartDateAsc(
            Long machineId, Date from, Date to);

    List<WorkOrderOperation> findByAssignedMachineIdAndStatusOrderByPlannedStartDateAsc(
            Long machineId, OperationStatus status);

    // ── Schedule view queries ──
    // All queries: exclude soft-deleted ops + cancelled ops + soft-deleted WOs

    @Query("SELECT op FROM WorkOrderOperation op " +
           "LEFT JOIN FETCH op.workOrder wo " +
           "LEFT JOIN FETCH op.workCenter wc " +
           "LEFT JOIN FETCH op.assignedMachine m " +
           "LEFT JOIN FETCH op.routingOperation ro " +
           "LEFT JOIN FETCH wo.bom b " +
           "LEFT JOIN FETCH b.parentInventoryItem " +
           "WHERE wc.id = :workCenterId " +
           "AND op.plannedStartDate >= :fromDate AND op.plannedStartDate < :toDate " +
           "AND op.deletedDate IS NULL AND wo.deletedDate IS NULL " +
           "AND op.status <> com.nextgenmanager.nextgenmanager.production.enums.OperationStatus.CANCELLED " +
           "ORDER BY op.plannedStartDate, op.sequence")
    List<WorkOrderOperation> findScheduleByWorkCenter(
            @Param("workCenterId") Integer workCenterId,
            @Param("fromDate") Date fromDate,
            @Param("toDate") Date toDate);

    @Query("SELECT op FROM WorkOrderOperation op " +
           "LEFT JOIN FETCH op.workOrder wo " +
           "LEFT JOIN FETCH op.workCenter wc " +
           "LEFT JOIN FETCH op.assignedMachine m " +
           "LEFT JOIN FETCH op.routingOperation ro " +
           "LEFT JOIN FETCH wo.bom b " +
           "LEFT JOIN FETCH b.parentInventoryItem " +
           "WHERE op.plannedStartDate >= :fromDate AND op.plannedStartDate < :toDate " +
           "AND op.status IN :statuses " +
           "AND op.deletedDate IS NULL AND wo.deletedDate IS NULL " +
           "ORDER BY wc.centerCode, op.plannedStartDate, op.sequence")
    List<WorkOrderOperation> findScheduleByDateRangeAndStatuses(
            @Param("fromDate") Date fromDate,
            @Param("toDate") Date toDate,
            @Param("statuses") List<OperationStatus> statuses);

    @Query("SELECT op FROM WorkOrderOperation op " +
           "LEFT JOIN FETCH op.workOrder wo " +
           "LEFT JOIN FETCH op.workCenter wc " +
           "LEFT JOIN FETCH op.assignedMachine m " +
           "LEFT JOIN FETCH op.routingOperation ro " +
           "LEFT JOIN FETCH wo.bom b " +
           "LEFT JOIN FETCH b.parentInventoryItem " +
           "WHERE m.id = :machineId " +
           "AND op.plannedStartDate >= :fromDate AND op.plannedStartDate < :toDate " +
           "AND op.deletedDate IS NULL AND wo.deletedDate IS NULL " +
           "AND op.status <> com.nextgenmanager.nextgenmanager.production.enums.OperationStatus.CANCELLED " +
           "ORDER BY op.plannedStartDate, op.sequence")
    List<WorkOrderOperation> findScheduleByMachine(
            @Param("machineId") Long machineId,
            @Param("fromDate") Date fromDate,
            @Param("toDate") Date toDate);
}
