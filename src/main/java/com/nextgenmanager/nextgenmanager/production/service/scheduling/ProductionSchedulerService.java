package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleResultDTO;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderEventType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.ScheduleDecisionLog;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeEvent;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.DowntimeEventRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.ResourceTimeline;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.Slot;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.WorkCalendar;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.WorkCalendarFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Finite-capacity forward scheduler for production Work Orders.
 *
 * <p>Every operation is placed, to the minute, in the earliest slot where its resource is both
 * open and free. The resource is the operation's machine, or its work centre when the work
 * centre has no machines. "Free" is judged against a ledger of everything already booked —
 * other work orders' operations, this work order's own earlier operations, and machine
 * downtime — so two jobs never share a machine.
 *
 * <p>Algorithm, per work order:
 * <ol>
 *   <li>Order the operations so each follows its predecessors: explicit dependencies when the
 *       routing declares any, otherwise the sequence within each line.</li>
 *   <li>For each, earliest start = the latest end among its predecessors. Where the work centre
 *       offers a choice of machines, take the one that finishes soonest.</li>
 *   <li>Book the slot, so everything scheduled afterwards plans around it.</li>
 * </ol>
 */
@Service
public class ProductionSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ProductionSchedulerService.class);

    // IST for Indian MSME
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final int MAX_SCHEDULING_HORIZON_DAYS = 365;

    private static final List<OperationStatus> FINISHED_OPERATIONS =
            List.of(OperationStatus.COMPLETED, OperationStatus.CANCELLED);

    private static final List<WorkOrderStatus> FINISHED_WORK_ORDERS = List.of(
            WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED,
            WorkOrderStatus.CANCELLED, WorkOrderStatus.SHORT_CLOSED);

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Autowired
    private DowntimeEventRepository downtimeEventRepository;

    @Autowired
    private WorkOrderAuditService auditService;

    @Autowired
    private EntityManager entityManager;

    private Clock clock = Clock.system(IST);

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Schedule all operations of a Work Order using forward scheduling.
     *
     * @param workOrderId the ID of the Work Order to schedule
     * @return ScheduleResultDTO with per-operation dates and warnings
     */
    @Transactional
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId) {
        return scheduleOne(workOrderId, null, "schedule");
    }

    /**
     * Reschedule a Work Order with a new start date.
     */
    @Transactional
    public ScheduleResultDTO rescheduleWorkOrder(int workOrderId, Date newStartDate) {
        return scheduleOne(workOrderId, newStartDate, "reschedule");
    }

    private ScheduleResultDTO scheduleOne(int workOrderId, Date overrideStartDate, String action) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found: " + workOrderId));

        // Allow scheduling from CREATED or SCHEDULED status
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "WorkOrder must be in CREATED or SCHEDULED status to " + action
                            + ". Current: " + workOrder.getWorkOrderStatus());
        }

        LocalDateTime now = now();
        return doSchedule(workOrder, overrideStartDate, loadLedger(now, Set.of(workOrder.getId())), now);
    }

    // ──────────────────────────────── scheduleAll ────────────────────────────────

    /**
     * Batch-schedule all CREATED Work Orders.
     * Sorted by priority (URGENT first) then due date (earliest first), so the orders that
     * matter most get first claim on capacity.
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

        LocalDateTime now = now();
        Ledger ledger = loadLedger(now, workOrders.stream().map(WorkOrder::getId).collect(Collectors.toSet()));

        List<ScheduleResultDTO> results = new ArrayList<>();
        for (WorkOrder wo : workOrders) {
            try {
                results.add(doSchedule(wo, null, ledger, now));
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

    // ──────────────────────────────── one work order ────────────────────────────────

    private ScheduleResultDTO doSchedule(WorkOrder workOrder, Date overrideStartDate, Ledger ledger, LocalDateTime now) {

        List<WorkOrderOperation> operations = workOrderOperationRepository
                .findByWorkOrderIdWithAssociationsOrderBySequence(workOrder.getId()).stream()
                .filter(op -> op.getDeletedDate() == null && op.getStatus() != OperationStatus.CANCELLED)
                .toList();

        if (operations.isEmpty()) {
            throw new IllegalStateException("WorkOrder has no operations to schedule");
        }

        // Plan against a private copy of the timelines it touches and commit only once the whole
        // work order fits. A failure part-way then leaves nothing half-booked, in the ledger or
        // on the operations.
        List<String> warnings = new ArrayList<>();
        Map<String, ResourceTimeline> draft = new HashMap<>();
        LocalDateTime woStartFrom = requestedStart(workOrder, overrideStartDate, now);
        List<Placement> placements = plan(workOrder, operations, woStartFrom, ledger, draft, warnings);
        ledger.timelines.putAll(draft);

        // ── Clean up previous schedule data (reschedule support) ──
        entityManager.createQuery(
                "DELETE FROM ScheduleDecisionLog d WHERE d.workOrder.id = :woId")
                .setParameter("woId", workOrder.getId())
                .executeUpdate();

        BigDecimal totalProductionMinutes = BigDecimal.ZERO;
        List<ScheduleResultDTO.OperationSchedule> opSchedules = new ArrayList<>();

        for (Placement p : placements) {
            WorkOrderOperation op = p.operation();
            Date opStart = toDate(p.slot().start());
            Date opEnd = toDate(p.slot().end());

            if (p.machine() != null) op.setAssignedMachine(p.machine());
            op.setPlannedStartDate(opStart);
            op.setPlannedEndDate(opEnd);
            workOrderOperationRepository.save(op);
            logDecision(workOrder, p);

            totalProductionMinutes = totalProductionMinutes.add(p.minutes());

            ScheduleResultDTO.OperationSchedule opSched = new ScheduleResultDTO.OperationSchedule();
            opSched.setOperationId(op.getId());
            opSched.setSequence(op.getSequence());
            opSched.setOperationName(op.getOperationName());
            opSched.setWorkCenterCode(p.workCenter().getCenterCode());
            if (p.machine() != null) {
                opSched.setMachineCode(p.machine().getMachineCode());
            }
            opSched.setPlannedStartDate(opStart);
            opSched.setPlannedEndDate(opEnd);
            opSched.setDurationMinutes(p.minutes());
            opSched.setParallelPath(op.getParallelPath());
            opSchedules.add(opSched);

            logger.info("Scheduled op {} ({}) at WC {} / Machine {} from {} to {} ({} min)",
                    op.getSequence(), op.getOperationName(), p.workCenter().getCenterCode(),
                    p.machine() != null ? p.machine().getMachineCode() : "N/A",
                    p.slot().start(), p.slot().end(), p.minutes());
        }

        // Sort by sequence number for display consistency
        opSchedules.sort(Comparator.comparingInt(ScheduleResultDTO.OperationSchedule::getSequence));

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

        checkDueDate(workOrder, woEnd, warnings);

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

        logger.info("Scheduled WorkOrder {} from {} to {} ({} operations, {} total min)",
                workOrder.getWorkOrderNumber(), woStart, woEnd, operations.size(), totalProductionMinutes);

        return result;
    }

    // ──────────────────────────────── planning ────────────────────────────────

    private record Placement(WorkOrderOperation operation, WorkCenter workCenter, MachineDetails machine,
                             Slot slot, BigDecimal minutes) {
    }

    /**
     * Places every operation in dependency order (Kahn's topological sort, lowest sequence
     * first among those that are ready), booking each into {@code draft} as it goes.
     */
    private List<Placement> plan(WorkOrder workOrder, List<WorkOrderOperation> operations, LocalDateTime startFrom,
                                 Ledger ledger, Map<String, ResourceTimeline> draft, List<String> warnings) {

        Map<Long, Set<Long>> predecessors = predecessors(operations);
        Map<Long, Integer> waitingOn = new HashMap<>();
        Map<Long, List<WorkOrderOperation>> successors = new HashMap<>();
        Map<Long, WorkOrderOperation> opById = operations.stream()
                .collect(Collectors.toMap(WorkOrderOperation::getId, op -> op));

        for (WorkOrderOperation op : operations) {
            Set<Long> preds = predecessors.get(op.getId());
            waitingOn.put(op.getId(), preds.size());
            for (Long predId : preds) {
                successors.computeIfAbsent(predId, k -> new ArrayList<>()).add(op);
            }
        }

        PriorityQueue<WorkOrderOperation> ready = new PriorityQueue<>(
                Comparator.comparing(WorkOrderOperation::getSequence).thenComparing(WorkOrderOperation::getId));
        operations.stream().filter(op -> waitingOn.get(op.getId()) == 0).forEach(ready::add);

        Map<Long, LocalDateTime> endById = new HashMap<>();
        List<Placement> placements = new ArrayList<>();

        while (!ready.isEmpty()) {
            WorkOrderOperation op = ready.poll();

            LocalDateTime earliest = startFrom;
            for (Long predId : predecessors.get(op.getId())) {
                LocalDateTime predEnd = endById.get(predId);
                if (predEnd.isAfter(earliest)) earliest = predEnd;
            }

            Placement placement = place(workOrder, op, earliest, ledger, draft, warnings);
            placements.add(placement);
            endById.put(op.getId(), placement.slot().end());

            for (WorkOrderOperation next : successors.getOrDefault(op.getId(), List.of())) {
                if (waitingOn.merge(next.getId(), -1, Integer::sum) == 0) ready.add(next);
            }
        }

        if (placements.size() < opById.size()) {
            throw new IllegalStateException(
                    "Cycle detected in operation dependencies for WorkOrder " + workOrder.getWorkOrderNumber());
        }
        return placements;
    }

    /**
     * What each operation must wait for. If any operation declares explicit dependencies, only
     * the declared ones apply and an operation with none starts with the work order. Otherwise
     * each operation follows the one before it in its own line — lines are separate items and
     * do not wait on each other.
     */
    private Map<Long, Set<Long>> predecessors(List<WorkOrderOperation> operations) {
        Map<Long, Set<Long>> predecessors = new HashMap<>();
        Set<Long> ids = operations.stream().map(WorkOrderOperation::getId).collect(Collectors.toSet());

        boolean explicit = operations.stream()
                .anyMatch(op -> op.getDependsOnOperationIds() != null && !op.getDependsOnOperationIds().isEmpty());

        if (explicit) {
            for (WorkOrderOperation op : operations) {
                Set<Long> preds = new HashSet<>();
                if (op.getDependsOnOperationIds() != null) {
                    op.getDependsOnOperationIds().stream().filter(ids::contains).forEach(preds::add);
                }
                predecessors.put(op.getId(), preds);
            }
            return predecessors;
        }

        Map<Long, WorkOrderOperation> previousInLine = new HashMap<>();
        for (WorkOrderOperation op : operations) { // already in sequence order
            Long lineKey = op.getWorkOrderLine() != null ? op.getWorkOrderLine().getId() : null;
            WorkOrderOperation previous = previousInLine.put(lineKey, op);
            predecessors.put(op.getId(), previous != null ? Set.of(previous.getId()) : Set.of());
        }
        return predecessors;
    }

    private Placement place(WorkOrder workOrder, WorkOrderOperation op, LocalDateTime earliest,
                            Ledger ledger, Map<String, ResourceTimeline> draft, List<String> warnings) {

        if (op.getWorkCenter() == null) {
            warnings.add("Operation " + op.getSequence() + " (" + op.getOperationName()
                    + ") has no work center assigned. Using WO-level work center.");
        }
        WorkCenter wc = op.getWorkCenter() != null ? op.getWorkCenter() : workOrder.getWorkCenter();
        if (wc == null) {
            throw new IllegalStateException(
                    "No work center assigned for operation " + op.getSequence() + " and no WO-level default");
        }
        if (wc.getWorkCenterStatus() == WorkCenter.WorkCenterStatus.UNDER_MAINTENANCE
                || wc.getWorkCenterStatus() == WorkCenter.WorkCenterStatus.SHUTDOWN) {
            warnings.add("Work center " + wc.getCenterCode() + " is " + wc.getWorkCenterStatus()
                    + "; operation " + op.getSequence() + " is planned on it regardless.");
        }

        WorkCalendar calendar = ledger.calendars.computeIfAbsent(wc.getId(), id -> WorkCalendarFactory.from(wc));
        if (!calendar.hasCapacity()) {
            throw new IllegalStateException("Work center " + wc.getCenterCode()
                    + " has no active shifts or available hours, so operation " + op.getSequence()
                    + " cannot be scheduled");
        }

        BigDecimal minutes = durationMinutes(workOrder, op);
        long wholeMinutes = minutes.setScale(0, RoundingMode.CEILING).longValue();

        // A null candidate means the work centre itself is the resource.
        List<MachineDetails> candidates = candidateMachines(op, wc, warnings);
        if (candidates.isEmpty()) candidates = Collections.singletonList(null);

        MachineDetails bestMachine = null;
        Slot bestSlot = null;
        for (MachineDetails machine : candidates) {
            ResourceTimeline timeline = timeline(resourceKey(machine, wc), machine, ledger, draft, false);
            Slot slot = timeline.findSlot(calendar, earliest, wholeMinutes, MAX_SCHEDULING_HORIZON_DAYS).orElse(null);
            if (slot == null) continue;
            if (bestSlot == null || slot.end().isBefore(bestSlot.end())
                    || (slot.end().equals(bestSlot.end()) && slot.start().isBefore(bestSlot.start()))) {
                bestSlot = slot;
                bestMachine = machine;
            }
        }
        if (bestSlot == null) {
            throw new IllegalStateException(
                    "Cannot find available capacity within " + MAX_SCHEDULING_HORIZON_DAYS
                            + " days for operation " + op.getSequence());
        }

        timeline(resourceKey(bestMachine, wc), bestMachine, ledger, draft, true)
                .block(bestSlot.start(), bestSlot.end());

        return new Placement(op, wc, bestMachine, bestSlot, minutes);
    }

    /**
     * The machines this operation may run on. One already assigned is kept — the routing or a
     * planner chose it — unless it is out of action and the routing does not insist on it, in
     * which case the operation moves to a working machine in the same work centre.
     */
    private List<MachineDetails> candidateMachines(WorkOrderOperation op, WorkCenter wc, List<String> warnings) {
        MachineDetails assigned = op.getAssignedMachine();
        boolean routingPinned = assigned != null && op.getRoutingOperation() != null
                && op.getRoutingOperation().getMachineDetails() != null
                && assigned.getId().equals(op.getRoutingOperation().getMachineDetails().getId());

        if (assigned != null && assigned.getMachineStatus() == MachineDetails.MachineStatus.ACTIVE) {
            return List.of(assigned);
        }

        List<MachineDetails> working = wc.getWorkStations() == null ? List.of()
                : wc.getWorkStations().stream()
                        .filter(m -> m.getDeletedDate() == null
                                && m.getMachineStatus() == MachineDetails.MachineStatus.ACTIVE)
                        .sorted(Comparator.comparing(MachineDetails::getMachineCode))
                        .toList();

        if (assigned == null) return working;

        if (routingPinned || working.isEmpty()) {
            warnings.add("Machine " + assigned.getMachineCode() + " is " + assigned.getMachineStatus()
                    + "; operation " + op.getSequence() + " is planned on it regardless.");
            return List.of(assigned);
        }
        warnings.add("Machine " + assigned.getMachineCode() + " is " + assigned.getMachineStatus()
                + "; operation " + op.getSequence() + " was moved to another machine in "
                + wc.getCenterCode() + ".");
        return working;
    }

    /** Total time = setupTime + runTime × the operation's own planned quantity. */
    private BigDecimal durationMinutes(WorkOrder workOrder, WorkOrderOperation op) {
        BigDecimal setupTime = BigDecimal.ZERO;
        BigDecimal runTime = BigDecimal.ZERO;
        if (op.getRoutingOperation() != null) {
            setupTime = op.getRoutingOperation().getSetupTime() != null
                    ? op.getRoutingOperation().getSetupTime() : BigDecimal.ZERO;
            runTime = op.getRoutingOperation().getRunTime() != null
                    ? op.getRoutingOperation().getRunTime() : BigDecimal.ZERO;
        }
        BigDecimal quantity = op.getPlannedQuantity() != null ? op.getPlannedQuantity()
                : workOrder.getPlannedQuantity() != null ? workOrder.getPlannedQuantity() : BigDecimal.ZERO;
        return setupTime.add(runTime.multiply(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    // ──────────────────────────────── capacity ledger ────────────────────────────────

    /** What is booked on every resource, and each work centre's calendar, for one scheduling run. */
    private static class Ledger {
        final Map<String, ResourceTimeline> timelines = new HashMap<>();
        final Map<Integer, WorkCalendar> calendars = new HashMap<>();
        final LocalDateTime now;

        Ledger(LocalDateTime now) {
            this.now = now;
        }
    }

    /**
     * Builds the ledger from live operations rather than a table of its own, so a booking
     * disappears the moment its operation is completed, cancelled or deleted. The work orders
     * being scheduled are left out: their old dates are about to be replaced.
     */
    private Ledger loadLedger(LocalDateTime now, Set<Integer> workOrdersBeingScheduled) {
        Ledger ledger = new Ledger(now);
        List<WorkOrderOperation> booked = workOrderOperationRepository.findBookedLoad(
                toDate(now), FINISHED_OPERATIONS, FINISHED_WORK_ORDERS);

        for (WorkOrderOperation op : booked) {
            if (workOrdersBeingScheduled.contains(op.getWorkOrder().getId())) continue;
            if (op.getWorkCenter() == null && op.getAssignedMachine() == null) continue;
            timeline(resourceKey(op.getAssignedMachine(), op.getWorkCenter()), op.getAssignedMachine(),
                    ledger, ledger.timelines, true)
                    .block(toLocal(op.getPlannedStartDate()), toLocal(op.getPlannedEndDate()));
        }
        return ledger;
    }

    /**
     * The timeline for a resource. A read looks through {@code draft} to the ledger; a write
     * goes to {@code draft}, copying the ledger's timeline in first. A machine's timeline starts
     * out with its downtime already blocked.
     */
    private ResourceTimeline timeline(String key, MachineDetails machine, Ledger ledger,
                                      Map<String, ResourceTimeline> draft, boolean forWrite) {
        ResourceTimeline drafted = draft.get(key);
        if (drafted != null) return drafted;

        ResourceTimeline base = ledger.timelines.computeIfAbsent(key, k -> {
            ResourceTimeline fresh = new ResourceTimeline();
            if (machine != null) {
                for (DowntimeEvent downtime : downtimeEventRepository
                        .findByMachineIdAndEndTimeAfter(machine.getId(), toDate(ledger.now))) {
                    fresh.block(toLocal(downtime.getStartTime()), toLocal(downtime.getEndTime()));
                }
            }
            return fresh;
        });
        if (!forWrite || draft == ledger.timelines) return base;

        ResourceTimeline copy = base.copy();
        draft.put(key, copy);
        return copy;
    }

    private static String resourceKey(MachineDetails machine, WorkCenter workCenter) {
        return machine != null ? "M:" + machine.getId() : "W:" + workCenter.getId();
    }

    // ──────────────────────────────── helpers ────────────────────────────────

    /** Work cannot be planned into the past: a start date that has gone by means "from now". */
    private LocalDateTime requestedStart(WorkOrder workOrder, Date overrideStartDate, LocalDateTime now) {
        Date requested = overrideStartDate != null ? overrideStartDate : workOrder.getPlannedStartDate();
        if (requested == null) return now;
        LocalDateTime start = toLocal(requested);
        return start.isBefore(now) ? now : start;
    }

    private void checkDueDate(WorkOrder workOrder, Date woEnd, List<String> warnings) {
        if (workOrder.getDueDate() == null) return;

        // A due date entered as a plain date means "by the end of that day".
        LocalDateTime due = toLocal(workOrder.getDueDate());
        if (due.toLocalTime().equals(LocalTime.MIDNIGHT)) due = due.plusDays(1);

        LocalDateTime end = toLocal(woEnd);
        if (!end.isAfter(due)) return;

        long lateMinutes = ChronoUnit.MINUTES.between(due, end);
        long diffDays = (lateMinutes + 24 * 60 - 1) / (24 * 60);
        warnings.add("Due date will be missed by " + diffDays + " day(s). "
                + "Scheduled end: " + woEnd + ", Due: " + workOrder.getDueDate());
    }

    private void logDecision(WorkOrder wo, Placement p) {
        WorkOrderOperation op = p.operation();

        ScheduleDecisionLog log = new ScheduleDecisionLog();
        log.setWorkOrder(wo);
        log.setWorkOrderOperation(op);
        log.setWorkCenter(p.workCenter());
        log.setMachine(p.machine());
        log.setScheduledDate(toDate(p.slot().start()));
        // One row per operation: the elapsed span it was given, and the working time it uses.
        log.setAvailableMinutes((int) ChronoUnit.MINUTES.between(p.slot().start(), p.slot().end()));
        log.setConsumedMinutes(p.minutes().intValue());
        log.setReason("Forward scheduling op " + op.getSequence()
                + " (" + op.getOperationName() + ") at WC " + p.workCenter().getCenterCode()
                + (p.machine() != null ? " / Machine " + p.machine().getMachineCode() : "")
                + ": " + p.slot().start() + " to " + p.slot().end());

        entityManager.persist(log);
    }

    /** Now, rounded up to the next whole minute. */
    private LocalDateTime now() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime truncated = now.truncatedTo(ChronoUnit.MINUTES);
        return truncated.equals(now) ? now : truncated.plusMinutes(1);
    }

    private static LocalDateTime toLocal(Date date) {
        // Not date.toInstant(): java.sql.Date refuses it, and a driver may hand one back.
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), IST).truncatedTo(ChronoUnit.MINUTES);
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(IST).toInstant());
    }
}
