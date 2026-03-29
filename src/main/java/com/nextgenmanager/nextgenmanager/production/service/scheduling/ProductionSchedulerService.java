package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleResultDTO;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderEventType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.ScheduleDecisionLog;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Finite-capacity forward scheduler for production Work Orders.
 *
 * Algorithm:
 * 1. Sort operations by sequence
 * 2. For each operation, starting from the previous operation's end date:
 *    - Get the work center
 *    - Calculate total time needed: setupTime + (runTime × plannedQuantity)
 *    - Fill available capacity day-by-day using WorkCenterCapacityService
 *    - Set operation's plannedStartDate and plannedEndDate
 * 3. Set WorkOrder.plannedEndDate = last operation's plannedEndDate
 * 4. Log decisions to ScheduleDecisionLog
 * 5. Return scheduling result with warnings (e.g., due date missed)
 */
@Service
public class ProductionSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ProductionSchedulerService.class);

    private static final int MAX_SCHEDULING_HORIZON_DAYS = 365;

    @Autowired
    private WorkCenterCapacityService capacityService;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Autowired
    private WorkOrderAuditService auditService;

    @Autowired
    private EntityManager entityManager;

    /**
     * Schedule all operations of a Work Order using forward scheduling.
     *
     * @param workOrderId the ID of the Work Order to schedule
     * @return ScheduleResultDTO with per-operation dates and warnings
     */
    @Transactional
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found: " + workOrderId));

        // Allow scheduling from CREATED or SCHEDULED status
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "WorkOrder must be in CREATED or SCHEDULED status to schedule. Current: " + workOrder.getWorkOrderStatus());
        }

        return doSchedule(workOrder, null);
    }

    /**
     * Reschedule a Work Order with a new start date.
     */
    @Transactional
    public ScheduleResultDTO rescheduleWorkOrder(int workOrderId, Date newStartDate) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found: " + workOrderId));

        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "WorkOrder must be in CREATED or SCHEDULED status to reschedule. Current: " + workOrder.getWorkOrderStatus());
        }

        return doSchedule(workOrder, newStartDate);
    }

    private ScheduleResultDTO doSchedule(WorkOrder workOrder, Date overrideStartDate) {

        List<String> warnings = new ArrayList<>();

        // ── Clean up previous schedule data (reschedule support) ──
        entityManager.createQuery(
                "DELETE FROM ScheduleDecisionLog d WHERE d.workOrder.id = :woId")
                .setParameter("woId", workOrder.getId())
                .executeUpdate();

        // Determine start date (IST for Indian MSME)
        ZoneId IST = ZoneId.of("Asia/Kolkata");
        LocalDate woStartDate;
        if (overrideStartDate != null) {
            woStartDate = overrideStartDate.toInstant().atZone(IST).toLocalDate();
        } else if (workOrder.getPlannedStartDate() != null) {
            woStartDate = workOrder.getPlannedStartDate().toInstant().atZone(IST).toLocalDate();
        } else {
            woStartDate = LocalDate.now(IST);
        }

        // Get operations sorted by sequence (eagerly fetched)
        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrderIdWithAssociationsOrderBySequence(workOrder.getId());

        if (operations.isEmpty()) {
            throw new IllegalStateException("WorkOrder has no operations to schedule");
        }

        BigDecimal[] totalMinutesAcc = {BigDecimal.ZERO};
        List<ScheduleResultDTO.OperationSchedule> opSchedules = new ArrayList<>();

        // Parallel mode: any op declares explicit dependency IDs
        boolean parallelMode = operations.stream()
                .anyMatch(op -> op.getDependsOnOperationIds() != null && !op.getDependsOnOperationIds().isEmpty());

        if (parallelMode) {
            scheduleParallel(workOrder, operations, woStartDate, warnings, IST, opSchedules, totalMinutesAcc);
        } else {
            scheduleSequential(workOrder, operations, woStartDate, warnings, IST, opSchedules, totalMinutesAcc);
        }

        BigDecimal totalProductionMinutes = totalMinutesAcc[0];

        // WO start = min op start; WO end = max op end (handles concurrent paths correctly)
        Date woStart = opSchedules.stream().map(ScheduleResultDTO.OperationSchedule::getPlannedStartDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        Date woEnd = opSchedules.stream().map(ScheduleResultDTO.OperationSchedule::getPlannedEndDate)
                .max(Comparator.naturalOrder()).orElseThrow();

        // Detect reschedule BEFORE overwriting scheduledAt
        boolean isReschedule = workOrder.getScheduledAt() != null;

        workOrder.setPlannedStartDate(woStart);
        workOrder.setPlannedEndDate(woEnd);
        workOrder.setEstimatedProductionMinutes(totalProductionMinutes.setScale(2, RoundingMode.HALF_UP));
        workOrder.setAutoScheduled(true);
        workOrder.setScheduledBy("SYSTEM");
        workOrder.setScheduledAt(new Date());
        workOrder.setWorkOrderStatus(WorkOrderStatus.SCHEDULED);

        workOrderRepository.save(workOrder);

        // Check due date
        if (workOrder.getDueDate() != null && woEnd.after(workOrder.getDueDate())) {
            long diffMs = woEnd.getTime() - workOrder.getDueDate().getTime();
            long diffDays = diffMs / (1000 * 60 * 60 * 24);
            warnings.add("Due date will be missed by " + diffDays + " day(s). "
                    + "Scheduled end: " + woEnd + ", Due: " + workOrder.getDueDate());
        }

        // Audit
        auditService.record(
                workOrder,
                isReschedule ? WorkOrderEventType.RESCHEDULED : WorkOrderEventType.SCHEDULED,
                "schedule",
                null,
                woStart + " → " + woEnd,
                "Auto-scheduled " + operations.size() + " operations"
        );

        // Build result
        ScheduleResultDTO result = new ScheduleResultDTO();
        result.setWorkOrderId(workOrder.getId());
        result.setWorkOrderNumber(workOrder.getWorkOrderNumber());
        result.setPlannedStartDate(woStart);
        result.setPlannedEndDate(woEnd);
        result.setEstimatedProductionMinutes(totalProductionMinutes);
        result.setEstimatedTotalCost(workOrder.getEstimatedTotalCost());
        result.setOperationSchedules(opSchedules);
        result.setWarnings(warnings);

        logger.info("Scheduled WorkOrder {} from {} to {} ({} operations, {} total min, parallel={})",
                workOrder.getWorkOrderNumber(), woStart, woEnd,
                operations.size(), totalProductionMinutes, parallelMode);

        return result;
    }

    // ──────────────────────────────── scheduling strategies ────────────────────────────────

    /**
     * Legacy sequential scheduling: each op starts on the same day the previous one ends
     * (or later if that work center has no remaining capacity that day).
     * The "skip to first available working day" guard inside scheduleOneOp handles overflow.
     */
    private void scheduleSequential(WorkOrder workOrder, List<WorkOrderOperation> operations,
            LocalDate woStartDate, List<String> warnings, ZoneId IST,
            List<ScheduleResultDTO.OperationSchedule> opSchedules, BigDecimal[] totalMinutesAcc) {

        LocalDate currentDate = woStartDate;
        for (WorkOrderOperation op : operations) {
            // Pass the same end date — scheduleOneOp will skip to next working day if exhausted
            currentDate = scheduleOneOp(workOrder, op, currentDate, warnings, IST, opSchedules, totalMinutesAcc);
        }
    }

    /**
     * Parallel scheduling using Kahn's BFS topological sort over the dependency DAG.
     * <p>
     * Independent operations (no dependencies) start from the WO start date concurrently.
     * A dependent operation's earliest start = max end date among all its blocking deps.
     * The capacity-skip guard inside scheduleOneOp handles exhausted days automatically.
     * WO end date = max of all operation end dates.
     */
    private void scheduleParallel(WorkOrder workOrder, List<WorkOrderOperation> operations,
            LocalDate woStartDate, List<String> warnings, ZoneId IST,
            List<ScheduleResultDTO.OperationSchedule> opSchedules, BigDecimal[] totalMinutesAcc) {

        Map<Long, WorkOrderOperation> opById = operations.stream()
                .collect(Collectors.toMap(WorkOrderOperation::getId, op -> op));
        Map<Long, LocalDate> opEndDateById = new HashMap<>();

        // Build in-degree and reverse graph (depId → set of ops that depend on it)
        Map<Long, Integer> inDegree = new HashMap<>();
        Map<Long, Set<Long>> dependents = new HashMap<>();

        for (WorkOrderOperation op : operations) {
            Long opId = op.getId();
            inDegree.putIfAbsent(opId, 0);
            dependents.putIfAbsent(opId, new HashSet<>());
            Set<Long> deps = op.getDependsOnOperationIds();
            if (deps != null) {
                for (Long depId : deps) {
                    inDegree.put(opId, inDegree.getOrDefault(opId, 0) + 1);
                    dependents.computeIfAbsent(depId, k -> new HashSet<>()).add(opId);
                }
            }
        }

        // Seed BFS queue with root ops (no incoming deps)
        Queue<Long> ready = new LinkedList<>();
        for (WorkOrderOperation op : operations) {
            if (inDegree.getOrDefault(op.getId(), 0) == 0) {
                ready.add(op.getId());
            }
        }

        int scheduled = 0;
        while (!ready.isEmpty()) {
            Long opId = ready.poll();
            WorkOrderOperation op = opById.get(opId);

            // Earliest start = max end date of all upstream deps (or WO start if none)
            LocalDate earliestStart = woStartDate;
            Set<Long> deps = op.getDependsOnOperationIds();
            if (deps != null) {
                for (Long depId : deps) {
                    LocalDate depEnd = opEndDateById.get(depId);
                    if (depEnd != null && depEnd.isAfter(earliestStart)) {
                        earliestStart = depEnd;
                    }
                }
            }

            // scheduleOneOp will skip past earliestStart if no capacity on that day
            LocalDate endDate = scheduleOneOp(workOrder, op, earliestStart, warnings, IST, opSchedules, totalMinutesAcc);
            opEndDateById.put(opId, endDate);
            scheduled++;

            // Decrement in-degree of dependents; enqueue those now unblocked
            for (Long dependentId : dependents.getOrDefault(opId, Collections.emptySet())) {
                int newDegree = inDegree.getOrDefault(dependentId, 0) - 1;
                inDegree.put(dependentId, newDegree);
                if (newDegree == 0) {
                    ready.add(dependentId);
                }
            }
        }

        if (scheduled < operations.size()) {
            throw new IllegalStateException(
                    "Cycle detected in operation dependencies for WorkOrder " + workOrder.getWorkOrderNumber());
        }

        // Sort by sequence number for display consistency
        opSchedules.sort(Comparator.comparingInt(ScheduleResultDTO.OperationSchedule::getSequence));
    }

    /**
     * Schedule a single operation starting from {@code startDate}.
     * Handles work center fallback, machine assignment, finite-capacity day-fill, and DB save.
     *
     * @return the last scheduled day (inclusive) — i.e. the operation's plannedEndDate as LocalDate.
     *         The next op may start on this same day if a different work center still has capacity.
     */
    private LocalDate scheduleOneOp(WorkOrder workOrder, WorkOrderOperation op, LocalDate startDate,
            List<String> warnings, ZoneId IST,
            List<ScheduleResultDTO.OperationSchedule> opSchedules, BigDecimal[] totalMinutesAcc) {

        WorkCenter wc = op.getWorkCenter();
        if (wc == null) {
            warnings.add("Operation " + op.getSequence() + " (" + op.getOperationName()
                    + ") has no work center assigned. Using WO-level work center.");
            wc = workOrder.getWorkCenter();
        }
        if (wc == null) {
            throw new IllegalStateException(
                    "No work center assigned for operation " + op.getSequence() + " and no WO-level default");
        }

        // Machine assignment: prefer pre-assigned, else pick least-loaded ACTIVE machine
        MachineDetails assignedMachine = op.getAssignedMachine();
        if (assignedMachine == null && wc.getWorkStations() != null) {
            assignedMachine = wc.getWorkStations().stream()
                    .filter(m -> m.getMachineStatus() == MachineDetails.MachineStatus.ACTIVE)
                    .min(Comparator.comparingInt(m -> getMachineLoad(m)))
                    .orElse(null);
            if (assignedMachine != null) {
                op.setAssignedMachine(assignedMachine);
                logger.info("Auto-assigned machine {} to op {} ({})",
                        assignedMachine.getMachineCode(), op.getSequence(), op.getOperationName());
            }
        }

        // Total time = setupTime + runTime × plannedQty
        BigDecimal setupTime = BigDecimal.ZERO;
        BigDecimal runTime = BigDecimal.ZERO;
        if (op.getRoutingOperation() != null) {
            setupTime = op.getRoutingOperation().getSetupTime() != null
                    ? op.getRoutingOperation().getSetupTime() : BigDecimal.ZERO;
            runTime = op.getRoutingOperation().getRunTime() != null
                    ? op.getRoutingOperation().getRunTime() : BigDecimal.ZERO;
        }
        BigDecimal totalOpMinutes = setupTime.add(
                runTime.multiply(workOrder.getPlannedQuantity())
        ).setScale(2, RoundingMode.HALF_UP);
        totalMinutesAcc[0] = totalMinutesAcc[0].add(totalOpMinutes);

        LocalDate currentDate = startDate;
        int daysIterated = 0;

        // Skip to first day with available capacity
        while (capacityService.getAvailableMinutes(wc, currentDate) == 0) {
            currentDate = currentDate.plusDays(1);
            daysIterated++;
            if (daysIterated > MAX_SCHEDULING_HORIZON_DAYS) {
                throw new IllegalStateException(
                        "Cannot find available capacity within " + MAX_SCHEDULING_HORIZON_DAYS
                                + " days for operation " + op.getSequence());
            }
        }
        LocalDate opStartDate = currentDate;

        // Forward-fill capacity day-by-day until operation is fully scheduled
        BigDecimal remainingMinutes = totalOpMinutes;
        while (remainingMinutes.compareTo(BigDecimal.ZERO) > 0) {
            int availableMinutes = capacityService.getAvailableMinutes(wc, currentDate);
            if (availableMinutes > 0) {
                BigDecimal consumed = remainingMinutes.min(BigDecimal.valueOf(availableMinutes));
                remainingMinutes = remainingMinutes.subtract(consumed);
                logDecision(workOrder, op, wc, assignedMachine, currentDate, availableMinutes, consumed);
            }
            if (remainingMinutes.compareTo(BigDecimal.ZERO) > 0) {
                currentDate = currentDate.plusDays(1);
                daysIterated++;
                if (daysIterated > MAX_SCHEDULING_HORIZON_DAYS) {
                    throw new IllegalStateException(
                            "Scheduling horizon exceeded for operation " + op.getSequence());
                }
            }
        }

        Date opStart = Date.from(opStartDate.atStartOfDay(IST).toInstant());
        Date opEnd = Date.from(currentDate.atStartOfDay(IST).toInstant());

        op.setPlannedStartDate(opStart);
        op.setPlannedEndDate(opEnd);
        workOrderOperationRepository.save(op);

        ScheduleResultDTO.OperationSchedule opSched = new ScheduleResultDTO.OperationSchedule();
        opSched.setOperationId(op.getId());
        opSched.setSequence(op.getSequence());
        opSched.setOperationName(op.getOperationName());
        opSched.setWorkCenterCode(wc.getCenterCode());
        if (assignedMachine != null) {
            opSched.setMachineCode(assignedMachine.getMachineCode());
        }
        opSched.setPlannedStartDate(opStart);
        opSched.setPlannedEndDate(opEnd);
        opSched.setDurationMinutes(totalOpMinutes);
        opSched.setParallelPath(op.getParallelPath());
        opSchedules.add(opSched);

        logger.info("Scheduled op {} ({}) at WC {} / Machine {} from {} to {} ({} min)",
                op.getSequence(), op.getOperationName(), wc.getCenterCode(),
                assignedMachine != null ? assignedMachine.getMachineCode() : "N/A",
                opStartDate, currentDate, totalOpMinutes);

        return currentDate;
    }

    // ──────────────────────────────── scheduleAll ────────────────────────────────

    /**
     * Batch-schedule all CREATED Work Orders.
     * Sorted by priority (URGENT first) then due date (earliest first).
     */
    @Transactional
    public List<ScheduleResultDTO> scheduleAll() {
        List<WorkOrder> workOrders = workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED);

        // Sort: priority rank ASC (URGENT=1 first), then dueDate ASC (earliest first)
        workOrders.sort((a, b) -> {
            int pa = a.getPriority() != null ? a.getPriority().getRank() : WorkOrderPriority.NORMAL.getRank();
            int pb = b.getPriority() != null ? b.getPriority().getRank() : WorkOrderPriority.NORMAL.getRank();
            if (pa != pb) return Integer.compare(pa, pb);
            if (a.getDueDate() != null && b.getDueDate() != null) {
                return a.getDueDate().compareTo(b.getDueDate());
            }
            if (a.getDueDate() != null) return -1;
            if (b.getDueDate() != null) return 1;
            return 0;
        });

        logger.info("Batch scheduling {} CREATED work orders", workOrders.size());

        List<ScheduleResultDTO> results = new ArrayList<>();
        for (WorkOrder wo : workOrders) {
            try {
                ScheduleResultDTO result = doSchedule(wo, null);
                results.add(result);
            } catch (Exception e) {
                logger.error("Failed to schedule WorkOrder {}: {}", wo.getWorkOrderNumber(), e.getMessage());
                ScheduleResultDTO errorResult = new ScheduleResultDTO();
                errorResult.setWorkOrderId(wo.getId());
                errorResult.setWorkOrderNumber(wo.getWorkOrderNumber());
                errorResult.setWarnings(List.of("Scheduling failed: " + e.getMessage()));
                results.add(errorResult);
            }
        }

        logger.info("Batch scheduling complete: {} processed", results.size());
        return results;
    }

    // ──────────────────────────────── helpers ────────────────────────────────

    /**
     * Get approximate load for a machine based on currently scheduled operations.
     */
    private int getMachineLoad(MachineDetails machine) {
        // Simple heuristic: count of scheduled operations on this machine
        // In a more advanced version, this would sum scheduled minutes
        return (int) workOrderOperationRepository
                .findByAssignedMachineIdAndStatusOrderByPlannedStartDateAsc(
                        machine.getId(),
                        com.nextgenmanager.nextgenmanager.production.enums.OperationStatus.PLANNED
                ).size();
    }

    private void logDecision(WorkOrder wo, WorkOrderOperation op, WorkCenter wc,
                              MachineDetails machine, LocalDate date,
                              int availableMinutes, BigDecimal consumedMinutes) {

        ScheduleDecisionLog log = new ScheduleDecisionLog();
        log.setWorkOrder(wo);
        log.setWorkOrderOperation(op);
        log.setWorkCenter(wc);
        log.setMachine(machine);
        log.setScheduledDate(Date.from(date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()));
        log.setAvailableMinutes(availableMinutes);
        log.setConsumedMinutes(consumedMinutes.intValue());
        log.setReason("Forward scheduling op " + op.getSequence()
                + " (" + op.getOperationName() + ") at WC " + wc.getCenterCode()
                + (machine != null ? " / Machine " + machine.getMachineCode() : ""));

        entityManager.persist(log);
    }
}
