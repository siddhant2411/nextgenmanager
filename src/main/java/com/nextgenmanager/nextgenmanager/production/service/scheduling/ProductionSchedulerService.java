package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleResultDTO;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderEventType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.ScheduleDecisionLog;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeEvent;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.DowntimeEventRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.DispatchRule;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.DispatchSequencer;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.ResourceTimeline;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.ScheduleDirection;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Finite-capacity scheduler for production Work Orders.
 *
 * <p>Every operation is placed, to the minute, in a slot where its resource is both open and
 * free. The resource is the operation's machine, or its work centre when the work centre has no
 * machines. "Free" is judged against a ledger of everything already booked — other work orders'
 * operations, this work order's own operations, and machine downtime — so two jobs never share
 * a machine.
 *
 * <p>Two directions:
 * <ul>
 *   <li><b>Forward</b> — each operation in the earliest slot after its predecessors.</li>
 *   <li><b>Backward</b> — the last operations finish by the due date and each earlier one takes
 *       the latest slot that still hands over in time. When that would need a start before now,
 *       or there is no due date, the order is scheduled forward instead and a warning says so.</li>
 * </ul>
 *
 * <p>Between two operations: the predecessor's move time, then the successor's queue time, both
 * wall-clock minutes. With a transfer quantity the predecessor passes its output on in batches,
 * so the successor may start once the first batch has been made and moved — but it cannot
 * finish before it has worked the last batch.
 *
 * <p>A batch run sequences its work orders by a {@link DispatchRule}; whatever comes first gets
 * first claim on capacity.
 */
@Service
public class ProductionSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ProductionSchedulerService.class);

    // IST for Indian MSME
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final int MAX_SCHEDULING_HORIZON_DAYS = 365;

    /** Rounds of nudging a slot later (or earlier) to satisfy an overlap constraint. */
    private static final int MAX_OVERLAP_ADJUSTMENTS = 50;

    private static final List<OperationStatus> FINISHED_OPERATIONS =
            List.of(OperationStatus.COMPLETED, OperationStatus.CANCELLED);

    /** Operations already under way or done: they no longer wait for anything. */
    private static final Set<OperationStatus> STARTED_OPERATIONS =
            EnumSet.of(OperationStatus.IN_PROGRESS, OperationStatus.COMPLETED);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    private static final List<WorkOrderStatus> FINISHED_WORK_ORDERS = List.of(
            WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED,
            WorkOrderStatus.CANCELLED, WorkOrderStatus.SHORT_CLOSED);

    /**
     * Work orders whose remaining operations may be planned again. A started order is among
     * them: its finished and running operations are held where they are and the rest are
     * planned around them.
     */
    private static final Set<WorkOrderStatus> REPLANNABLE_WORK_ORDERS = EnumSet.of(
            WorkOrderStatus.CREATED, WorkOrderStatus.SCHEDULED, WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.MATERIAL_PENDING, WorkOrderStatus.READY_FOR_PRODUCTION,
            WorkOrderStatus.PARTIALLY_READY, WorkOrderStatus.MATERIAL_REORDER, WorkOrderStatus.HOLD);

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Autowired
    private WorkOrderMaterialRepository workOrderMaterialRepository;

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

    /** Schedule all operations of a Work Order forward from its start date, or from now. */
    @Transactional
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId) {
        return scheduleWorkOrder(workOrderId, ScheduleDirection.FORWARD);
    }

    @Transactional
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId, ScheduleDirection direction) {
        return scheduleOne(workOrderId, null, direction, "schedule");
    }

    /** Reschedule a Work Order forward from a new start date. */
    @Transactional
    public ScheduleResultDTO rescheduleWorkOrder(int workOrderId, Date newStartDate) {
        return scheduleOne(workOrderId, newStartDate, ScheduleDirection.FORWARD, "reschedule");
    }

    private ScheduleResultDTO scheduleOne(int workOrderId, Date overrideStartDate, ScheduleDirection direction,
                                          String action) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found: " + workOrderId));

        if (!REPLANNABLE_WORK_ORDERS.contains(workOrder.getWorkOrderStatus())) {
            throw new IllegalStateException(
                    "A work order that is " + workOrder.getWorkOrderStatus() + " cannot be " + action
                            + "d. It must be one of " + REPLANNABLE_WORK_ORDERS + ".");
        }

        LocalDateTime now = now();
        Ledger ledger = loadLedger(now, Set.of(workOrder.getId()));
        return doSchedule(workOrder, loadOperations(workOrder), overrideStartDate, direction, null, ledger, now);
    }

    // ──────────────────────────────── scheduleAll ────────────────────────────────

    /** Batch-schedule all CREATED Work Orders forward, by priority then due date. */
    @Transactional
    public List<ScheduleResultDTO> scheduleAll() {
        return scheduleAll(DispatchRule.PRIORITY, ScheduleDirection.FORWARD);
    }

    /**
     * Batch-schedule all CREATED Work Orders, sequenced by {@code rule} so the orders that
     * matter most under it get first claim on capacity. One that fails is reported and leaves
     * nothing booked; the rest carry on.
     *
     * <p>Sub-assemblies in the batch come in levels. Forward, a sub-assembly is planned before
     * the order that uses it, so that order can wait for it. Backward, the order that uses it is
     * planned first, and the sub-assembly is then due when it is needed. The rule sequences the
     * orders within each level.
     */
    @Transactional
    public List<ScheduleResultDTO> scheduleAll(DispatchRule rule, ScheduleDirection direction) {
        List<WorkOrder> workOrders = workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED);

        logger.info("Batch scheduling {} CREATED work orders ({}, {})", workOrders.size(), rule, direction);

        LocalDateTime now = now();
        Ledger ledger = loadLedger(now, workOrders.stream().map(WorkOrder::getId).collect(Collectors.toSet()));

        Map<Integer, List<WorkOrderOperation>> operationsByOrder = new HashMap<>();
        Map<Integer, DispatchSequencer.Job<WorkOrder>> jobs = new HashMap<>();
        for (WorkOrder wo : workOrders) {
            List<WorkOrderOperation> operations = loadOperations(wo);
            operationsByOrder.put(wo.getId(), operations);
            jobs.put(wo.getId(), dispatchJob(wo, operations, ledger, now));
        }

        Map<Integer, List<SubAssembly>> subAssembliesInBatch = new HashMap<>();
        for (WorkOrder wo : workOrders) {
            subAssembliesInBatch.put(wo.getId(), subAssemblies(wo).stream()
                    .filter(sub -> jobs.containsKey(sub.child().getId())).toList());
        }
        Map<Integer, Integer> levels = new HashMap<>();
        for (WorkOrder wo : workOrders) level(wo.getId(), subAssembliesInBatch, levels, new HashSet<>());
        TreeMap<Integer, List<DispatchSequencer.Job<WorkOrder>>> byLevel = new TreeMap<>(
                direction == ScheduleDirection.BACKWARD ? Comparator.<Integer>reverseOrder() : Comparator.<Integer>naturalOrder());
        for (WorkOrder wo : workOrders) {
            byLevel.computeIfAbsent(levels.get(wo.getId()), k -> new ArrayList<>()).add(jobs.get(wo.getId()));
        }
        List<DispatchSequencer.Job<WorkOrder>> sequence = new ArrayList<>();
        byLevel.values().forEach(level -> sequence.addAll(DispatchSequencer.sequence(level, rule)));

        // Backward: when the orders already planned need each sub-assembly's output.
        Map<Integer, LocalDateTime> neededBy = new HashMap<>();

        List<ScheduleResultDTO> results = new ArrayList<>();
        for (DispatchSequencer.Job<WorkOrder> job : sequence) {
            WorkOrder wo = job.ref();
            try {
                List<WorkOrderOperation> operations = operationsByOrder.get(wo.getId());
                LocalDateTime readyBy = neededBy.get(wo.getId());
                LocalDateTime dueOverride = readyBy == null ? null
                        : readyBy.minusMinutes(finalMoveMinutes(operations));
                results.add(doSchedule(wo, operations, null, direction, dueOverride, ledger, now));
                if (direction == ScheduleDirection.BACKWARD) {
                    for (SubAssembly sub : subAssembliesInBatch.get(wo.getId())) {
                        LocalDateTime needed = neededAt(sub, operations);
                        if (needed != null) neededBy.merge(sub.child().getId(), needed, ProductionSchedulerService::earlier);
                    }
                }
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

    /**
     * What the dispatch rule needs to know about an order. Time left until the due date is
     * measured in productive minutes on the calendar of the order's first work centre, so it
     * can be set against the work to do.
     */
    private DispatchSequencer.Job<WorkOrder> dispatchJob(WorkOrder wo, List<WorkOrderOperation> operations,
                                                         Ledger ledger, LocalDateTime now) {
        int rank = wo.getPriority() != null ? wo.getPriority().getRank() : WorkOrderPriority.NORMAL.getRank();
        double work = operations.stream().mapToDouble(op -> durationMinutes(wo, op).doubleValue()).sum();
        LocalDateTime due = dueBy(wo);
        if (due == null) return new DispatchSequencer.Job<>(wo, rank, null, work, 0);

        WorkCenter wc = operations.stream().map(WorkOrderOperation::getWorkCenter).filter(Objects::nonNull)
                .findFirst().orElse(wo.getWorkCenter());
        WorkCalendar calendar = wc == null ? null
                : ledger.calendars.computeIfAbsent(wc.getId(), id -> WorkCalendarFactory.from(wc));
        double timeToDue = calendar != null && calendar.hasCapacity()
                ? calendar.productiveMinutesBetween(now, due)
                : ChronoUnit.MINUTES.between(now, due);
        return new DispatchSequencer.Job<>(wo, rank, due, work, timeToDue);
    }

    // ──────────────────────────────── one work order ────────────────────────────────

    private List<WorkOrderOperation> loadOperations(WorkOrder workOrder) {
        return workOrderOperationRepository
                .findByWorkOrderIdWithAssociationsOrderBySequence(workOrder.getId()).stream()
                .filter(op -> op.getDeletedDate() == null && op.getStatus() != OperationStatus.CANCELLED)
                .toList();
    }

    /**
     * @param dueOverride when, in a batch, the order that uses this one's output needs it; the
     *                    order is then planned back from that or its own due date, whichever is
     *                    sooner
     */
    private ScheduleResultDTO doSchedule(WorkOrder workOrder, List<WorkOrderOperation> operations,
                                         Date overrideStartDate, ScheduleDirection direction,
                                         LocalDateTime dueOverride, Ledger ledger, LocalDateTime now) {

        if (operations.isEmpty()) {
            throw new IllegalStateException("WorkOrder has no operations to schedule");
        }

        Map<Long, Set<Long>> predecessors = predecessors(operations);
        List<WorkOrderOperation> order = topologicalOrder(workOrder, operations, predecessors);

        // Plan against a private copy of the timelines it touches and commit only once the whole
        // work order fits. A failure part-way then leaves nothing half-booked, in the ledger or
        // on the operations.
        List<String> warnings = new ArrayList<>();
        Map<String, ResourceTimeline> draft = null;
        List<Placement> placements = null;
        ScheduleDirection used = ScheduleDirection.FORWARD;
        Map<Long, Floor> floors = floors(workOrder, operations, predecessors, ledger, warnings);

        if (direction == ScheduleDirection.BACKWARD) {
            LocalDateTime due = dueBy(workOrder);
            if (dueOverride != null && (due == null || dueOverride.isBefore(due))) {
                due = dueOverride;
                warnings.add("Planned to be ready by " + fmt(dueOverride.plusMinutes(finalMoveMinutes(operations)))
                        + ", when " + workOrder.getParentWorkOrder().getWorkOrderNumber() + " needs it.");
            }
            if (due == null) {
                warnings.add("There is no due date to schedule back from, so the order was scheduled forward.");
            } else {
                List<String> attemptWarnings = new ArrayList<>();
                Map<String, ResourceTimeline> attemptDraft = new HashMap<>();
                try {
                    Map<Long, Placement> pinned = pin(workOrder, operations, ledger, attemptDraft, attemptWarnings);
                    placements = planBackward(workOrder, order, predecessors, pinned, floors, due, now, ledger,
                            attemptDraft, attemptWarnings);
                    draft = attemptDraft;
                    warnings.addAll(attemptWarnings);
                    used = ScheduleDirection.BACKWARD;
                } catch (BackwardInfeasible e) {
                    warnings.add("Cannot finish by the due date working back from it (" + e.getMessage()
                            + "), so the order was scheduled forward from now.");
                }
            }
        }
        if (placements == null) {
            draft = new HashMap<>();
            Map<Long, Placement> pinned = pin(workOrder, operations, ledger, draft, warnings);
            LocalDateTime startFrom = requestedStart(workOrder, overrideStartDate, now);
            placements = planForward(workOrder, order, predecessors, pinned, floors, startFrom, ledger, draft, warnings);
        }
        ledger.timelines.putAll(draft);

        LocalDateTime ready = placements.stream()
                .map(p -> p.slot().end().plusMinutes(moveMinutes(p.operation())))
                .max(Comparator.naturalOrder()).orElseThrow();
        ledger.readyAt.put(workOrder.getId(), ready);
        checkParentNeeds(workOrder, ready, ledger, warnings);

        // ── Clean up previous schedule data (reschedule support) ──
        entityManager.createQuery(
                "DELETE FROM ScheduleDecisionLog d WHERE d.workOrder.id = :woId")
                .setParameter("woId", workOrder.getId())
                .executeUpdate();

        BigDecimal totalProductionMinutes = BigDecimal.ZERO;
        List<ScheduleResultDTO.OperationSchedule> opSchedules = new ArrayList<>();
        int planned = 0;

        for (Placement p : placements) {
            WorkOrderOperation op = p.operation();
            Date opStart = toDate(p.slot().start());
            Date opEnd = toDate(p.slot().end());

            // Completed and on-hold operations keep what they have. A running one keeps its
            // start and gets the end it is now heading for.
            if (p.pin() == null || p.pin() == Pin.IN_PROGRESS) {
                if (p.machine() != null) op.setAssignedMachine(p.machine());
                if (p.pin() == null || op.getPlannedStartDate() == null) op.setPlannedStartDate(opStart);
                op.setPlannedEndDate(opEnd);
                workOrderOperationRepository.save(op);
            }
            if (p.pin() == null) planned++;
            logDecision(workOrder, p, used);

            totalProductionMinutes = totalProductionMinutes.add(p.minutes());

            ScheduleResultDTO.OperationSchedule opSched = new ScheduleResultDTO.OperationSchedule();
            opSched.setOperationId(op.getId());
            opSched.setSequence(op.getSequence());
            opSched.setOperationName(op.getOperationName());
            if (p.workCenter() != null) opSched.setWorkCenterCode(p.workCenter().getCenterCode());
            if (p.machine() != null) {
                opSched.setMachineCode(p.machine().getMachineCode());
            }
            opSched.setPlannedStartDate(opStart);
            opSched.setPlannedEndDate(opEnd);
            opSched.setDurationMinutes(p.minutes());
            opSched.setParallelPath(op.getParallelPath());
            opSched.setStatus(op.getStatus() != null ? op.getStatus().name() : null);
            opSched.setPinned(p.pin() != null);
            opSchedules.add(opSched);

            logger.info("{} op {} ({}) at WC {} / Machine {} from {} to {} ({} min)",
                    p.pin() == null ? "Scheduled" : "Kept " + p.pin(),
                    op.getSequence(), op.getOperationName(),
                    p.workCenter() != null ? p.workCenter().getCenterCode() : "N/A",
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
        // Only a new order moves to SCHEDULED. One that has started, is waiting on material or
        // is on hold keeps saying so; it just has new dates.
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.CREATED) {
            workOrder.setWorkOrderStatus(WorkOrderStatus.SCHEDULED);
        }

        workOrderRepository.save(workOrder);

        checkDueDate(workOrder, woEnd, warnings);

        int kept = placements.size() - planned;
        // Audit
        auditService.record(
                workOrder,
                isReschedule ? WorkOrderEventType.RESCHEDULED : WorkOrderEventType.SCHEDULED,
                "schedule",
                null,
                woStart + " → " + woEnd,
                "Auto-scheduled " + planned + " operations "
                        + (used == ScheduleDirection.BACKWARD ? "back from the due date" : "forward")
                        + (kept > 0 ? "; kept " + kept + " completed, running or on-hold operations where they are" : "")
        );

        // Build result
        ScheduleResultDTO result = new ScheduleResultDTO();
        result.setWorkOrderId(workOrder.getId());
        result.setWorkOrderNumber(workOrder.getWorkOrderNumber());
        result.setPlannedStartDate(woStart);
        result.setPlannedEndDate(woEnd);
        result.setEstimatedProductionMinutes(totalProductionMinutes);
        result.setEstimatedTotalCost(workOrder.getEstimatedTotalCost());
        result.setDirection(used.name());
        result.setOperationSchedules(opSchedules);
        result.setWarnings(warnings);

        logger.info("Scheduled WorkOrder {} {} from {} to {} ({} operations, {} total min)",
                workOrder.getWorkOrderNumber(), used, woStart, woEnd, operations.size(), totalProductionMinutes);

        return result;
    }

    // ──────────────────────────────── planning ────────────────────────────────

    /**
     * @param handOverAt when the first batch of output is ready to move on: the end of the slot,
     *                   unless the operation passes its output on in transfer batches
     * @param pin        why the operation was held where it is, or null when it was planned
     */
    private record Placement(WorkOrderOperation operation, WorkCenter workCenter, MachineDetails machine,
                             WorkCalendar calendar, Slot slot, BigDecimal minutes, LocalDateTime handOverAt,
                             Pin pin) {
    }

    /** Why an operation is held where it is instead of being planned. */
    private enum Pin {
        /** Done: it holds nothing, and its successors go from when it actually ended. */
        COMPLETED,
        /** Running: it keeps its machine and start, and ends when its remaining quantity is made. */
        IN_PROGRESS,
        /** On hold: it keeps its planned dates and machine until a planner moves it. */
        HOLD
    }

    /** Where an operation runs, before a machine is chosen. */
    private record Station(WorkCenter workCenter, WorkCalendar calendar) {
    }

    /** Backward scheduling cannot fit the order between now and its due date. */
    private static class BackwardInfeasible extends RuntimeException {
        BackwardInfeasible(String message) {
            super(message);
        }
    }

    /**
     * Places every operation, predecessors first, each in the earliest slot after its
     * predecessors have handed over and their output has moved and queued.
     */
    private List<Placement> planForward(WorkOrder workOrder, List<WorkOrderOperation> order,
                                        Map<Long, Set<Long>> predecessors, Map<Long, Placement> pinned,
                                        Map<Long, Floor> floors, LocalDateTime startFrom, Ledger ledger,
                                        Map<String, ResourceTimeline> draft, List<String> warnings) {
        Map<Long, Placement> placed = new HashMap<>();
        List<Placement> placements = new ArrayList<>();

        for (WorkOrderOperation op : order) {
            Placement held = pinned.get(op.getId());
            if (held != null) {
                placements.add(held);
                placed.put(op.getId(), held);
                continue;
            }
            Station station = station(workOrder, op, ledger, warnings);

            LocalDateTime earliest = startFrom;
            LocalDateTime finishNoSoonerThan = null;
            for (Long predId : predecessors.get(op.getId())) {
                Placement pred = placed.get(predId);
                LocalDateTime arrives = pred.handOverAt().plusMinutes(moveMinutes(pred.operation()));
                earliest = later(earliest, arrives.plusMinutes(queueMinutes(op)));

                BigDecimal batch = transferBatch(workOrder, pred.operation());
                if (batch != null) {
                    // The last batch arrives when the predecessor ends, and still has to be worked.
                    LocalDateTime lastArrives = pred.slot().end().plusMinutes(moveMinutes(pred.operation()));
                    double lastBatchWork = runPerUnit(op).multiply(batch).doubleValue();
                    LocalDateTime finish = station.calendar()
                            .advance(lastArrives, lastBatchWork, MAX_SCHEDULING_HORIZON_DAYS).orElse(lastArrives);
                    finishNoSoonerThan = finishNoSoonerThan == null ? finish : later(finishNoSoonerThan, finish);
                }
            }

            Floor floor = floors.get(op.getId());
            if (floor != null && floor.at().isAfter(earliest)) {
                earliest = floor.at();
                warnings.add("Operation " + op.getSequence() + " (" + op.getOperationName() + ") waits for "
                        + floor.reason() + ", ready " + fmt(floor.at()) + ".");
            }

            Placement placement = placeForward(workOrder, op, station, earliest, finishNoSoonerThan,
                    ledger, draft, warnings);
            placements.add(placement);
            placed.put(op.getId(), placement);
        }
        return placements;
    }

    /**
     * Places every operation, successors first, each in the latest slot that still hands its
     * output over in time for every successor. The final operations end by {@code due}.
     */
    private List<Placement> planBackward(WorkOrder workOrder, List<WorkOrderOperation> order,
                                         Map<Long, Set<Long>> predecessors, Map<Long, Placement> pinned,
                                         Map<Long, Floor> floors, LocalDateTime due, LocalDateTime floor, Ledger ledger,
                                         Map<String, ResourceTimeline> draft, List<String> warnings) {
        Map<Long, List<Long>> successors = new HashMap<>();
        predecessors.forEach((opId, preds) ->
                preds.forEach(predId -> successors.computeIfAbsent(predId, k -> new ArrayList<>()).add(opId)));

        Map<Long, Placement> placed = new HashMap<>();
        List<Placement> placements = new ArrayList<>();

        List<WorkOrderOperation> reversed = new ArrayList<>(order);
        Collections.reverse(reversed);
        for (WorkOrderOperation op : reversed) {
            Placement held = pinned.get(op.getId());
            if (held != null) {
                placements.add(held);
                placed.put(op.getId(), held);
                continue;
            }
            Station station = station(workOrder, op, ledger, warnings);
            BigDecimal batch = transferBatch(workOrder, op);

            LocalDateTime latestEnd = due;
            LocalDateTime handOverBy = null;
            for (Long succId : successors.getOrDefault(op.getId(), List.of())) {
                Placement succ = placed.get(succId);
                LocalDateTime leaveBy = succ.slot().start()
                        .minusMinutes(queueMinutes(succ.operation()))
                        .minusMinutes(moveMinutes(op));
                // A held successor has its start already; there is no batch overlap to plan into it.
                if (batch == null || succ.pin() != null) {
                    latestEnd = earlier(latestEnd, leaveBy);
                    continue;
                }
                handOverBy = handOverBy == null ? leaveBy : earlier(handOverBy, leaveBy);
                // The last batch must reach the successor in time for it to be worked before it ends.
                double lastBatchWork = runPerUnit(succ.operation()).multiply(batch).doubleValue();
                LocalDateTime lastArriveBy = succ.calendar()
                        .retreat(succ.slot().end(), lastBatchWork, MAX_SCHEDULING_HORIZON_DAYS)
                        .orElse(succ.slot().start());
                latestEnd = earlier(latestEnd, lastArriveBy.minusMinutes(moveMinutes(op)));
            }

            // Nothing may start before now, nor before what the operation waits for is there.
            Floor waitsFor = floors.get(op.getId());
            Placement placement = waitsFor != null && waitsFor.at().isAfter(floor)
                    ? placeBackward(workOrder, op, station, latestEnd, handOverBy, waitsFor.at(),
                            waitsFor.reason() + " is ready", ledger, draft, warnings)
                    : placeBackward(workOrder, op, station, latestEnd, handOverBy, floor, "now",
                            ledger, draft, warnings);
            placements.add(placement);
            placed.put(op.getId(), placement);
        }
        return placements;
    }

    /**
     * Operations in an order where each comes after all its predecessors (Kahn's sort, lowest
     * sequence first among those that are ready).
     */
    private List<WorkOrderOperation> topologicalOrder(WorkOrder workOrder, List<WorkOrderOperation> operations,
                                                      Map<Long, Set<Long>> predecessors) {
        Map<Long, Integer> waitingOn = new HashMap<>();
        Map<Long, List<WorkOrderOperation>> successors = new HashMap<>();
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

        List<WorkOrderOperation> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            WorkOrderOperation op = ready.poll();
            order.add(op);
            for (WorkOrderOperation next : successors.getOrDefault(op.getId(), List.of())) {
                if (waitingOn.merge(next.getId(), -1, Integer::sum) == 0) ready.add(next);
            }
        }

        if (order.size() < operations.size()) {
            throw new IllegalStateException(
                    "Cycle detected in operation dependencies for WorkOrder " + workOrder.getWorkOrderNumber());
        }
        return order;
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

    private Station station(WorkOrder workOrder, WorkOrderOperation op, Ledger ledger, List<String> warnings) {
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
        return new Station(wc, calendar);
    }

    /**
     * The earliest slot from {@code earliest}, on the machine that finishes soonest. With
     * {@code finishNoSoonerThan} set, a slot that would end too early is pushed later until it
     * does not.
     */
    private Placement placeForward(WorkOrder workOrder, WorkOrderOperation op, Station station,
                                   LocalDateTime earliest, LocalDateTime finishNoSoonerThan,
                                   Ledger ledger, Map<String, ResourceTimeline> draft, List<String> warnings) {
        BigDecimal minutes = durationMinutes(workOrder, op);
        long wholeMinutes = minutes.setScale(0, RoundingMode.CEILING).longValue();

        MachineDetails bestMachine = null;
        Slot bestSlot = null;
        for (MachineDetails machine : candidates(op, station.workCenter(), warnings)) {
            ResourceTimeline timeline = timeline(resourceKey(machine, station.workCenter()), machine, ledger, draft, false);
            Slot slot = timeline.findSlot(station.calendar(), earliest, wholeMinutes, MAX_SCHEDULING_HORIZON_DAYS)
                    .orElse(null);
            for (int i = 0; slot != null && finishNoSoonerThan != null
                    && slot.end().isBefore(finishNoSoonerThan) && i < MAX_OVERLAP_ADJUSTMENTS; i++) {
                LocalDateTime later = slot.start().plusMinutes(ChronoUnit.MINUTES.between(slot.end(), finishNoSoonerThan));
                slot = timeline.findSlot(station.calendar(), later, wholeMinutes, MAX_SCHEDULING_HORIZON_DAYS)
                        .orElse(null);
            }
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
        return book(workOrder, op, station, bestMachine, bestSlot, minutes, ledger, draft);
    }

    /**
     * The latest slot ending by {@code latestEnd} and starting no earlier than {@code floor}, on
     * the machine that can start latest. With {@code handOverBy} set, a slot whose first batch
     * would be ready too late is pulled earlier until it is not.
     */
    private Placement placeBackward(WorkOrder workOrder, WorkOrderOperation op, Station station,
                                    LocalDateTime latestEnd, LocalDateTime handOverBy, LocalDateTime floor,
                                    String floorReason, Ledger ledger, Map<String, ResourceTimeline> draft,
                                    List<String> warnings) {
        BigDecimal minutes = durationMinutes(workOrder, op);
        long wholeMinutes = minutes.setScale(0, RoundingMode.CEILING).longValue();

        MachineDetails bestMachine = null;
        Slot bestSlot = null;
        if (latestEnd.isAfter(floor)) {
            for (MachineDetails machine : candidates(op, station.workCenter(), warnings)) {
                ResourceTimeline timeline = timeline(resourceKey(machine, station.workCenter()), machine, ledger, draft, false);
                Slot slot = timeline.findLatestSlot(station.calendar(), latestEnd, wholeMinutes, floor).orElse(null);
                for (int i = 0; slot != null && handOverBy != null && i < MAX_OVERLAP_ADJUSTMENTS; i++) {
                    LocalDateTime handOver = handOverAt(workOrder, op, station.calendar(), slot);
                    if (!handOver.isAfter(handOverBy)) break;
                    LocalDateTime earlier = slot.end().minusMinutes(ChronoUnit.MINUTES.between(handOverBy, handOver));
                    slot = timeline.findLatestSlot(station.calendar(), earlier, wholeMinutes, floor).orElse(null);
                }
                if (slot == null) continue;
                if (bestSlot == null || slot.start().isAfter(bestSlot.start())
                        || (slot.start().equals(bestSlot.start()) && slot.end().isAfter(bestSlot.end()))) {
                    bestSlot = slot;
                    bestMachine = machine;
                }
            }
        }
        if (bestSlot == null) {
            throw new BackwardInfeasible("operation " + op.getSequence() + " would have to start before " + floorReason);
        }
        return book(workOrder, op, station, bestMachine, bestSlot, minutes, ledger, draft);
    }

    private Placement book(WorkOrder workOrder, WorkOrderOperation op, Station station, MachineDetails machine,
                           Slot slot, BigDecimal minutes, Ledger ledger, Map<String, ResourceTimeline> draft) {
        timeline(resourceKey(machine, station.workCenter()), machine, ledger, draft, true)
                .block(slot.start(), slot.end());
        return new Placement(op, station.workCenter(), machine, station.calendar(), slot, minutes,
                handOverAt(workOrder, op, station.calendar(), slot), null);
    }

    // ──────────────────────────────── waiting for inputs ────────────────────────────────

    /** An operation may not start before {@code at}, because it waits for {@code reason}. */
    private record Floor(LocalDateTime at, String reason) {
    }

    /** An order making a part its parent uses, and the parent's materials it supplies. */
    private record SubAssembly(WorkOrder child, List<WorkOrderMaterial> consumedBy) {
    }

    /**
     * When each operation can start at the earliest, because of what it waits for: here, the
     * sub-assemblies this order uses. Operations with nothing to wait for are left out.
     */
    private Map<Long, Floor> floors(WorkOrder workOrder, List<WorkOrderOperation> operations,
                                    Map<Long, Set<Long>> predecessors, Ledger ledger, List<String> warnings) {
        Map<Long, Floor> floors = new HashMap<>();
        for (SubAssembly sub : subAssemblies(workOrder)) {
            WorkOrder child = sub.child();
            // Whatever a finished sub-assembly made is already in stock.
            if (FINISHED_WORK_ORDERS.contains(child.getWorkOrderStatus())) continue;

            LocalDateTime ready = readyAt(child, ledger);
            if (ready == null) {
                warnings.add("Sub-assembly " + child.getWorkOrderNumber() + " is not scheduled yet, so this order "
                        + "was planned without waiting for it. Schedule " + child.getWorkOrderNumber() + " first.");
                continue;
            }
            Floor floor = new Floor(ready, "sub-assembly " + child.getWorkOrderNumber());
            for (WorkOrderMaterial material : sub.consumedBy()) {
                for (Long opId : consumers(material, operations, predecessors)) {
                    floors.merge(opId, floor, (a, b) -> b.at().isAfter(a.at()) ? b : a);
                }
            }
        }
        return floors;
    }

    /**
     * The orders raised to make a part this order uses. A split and a rework order also point
     * at the order they came from, but make nothing it consumes, so matching on the parent's
     * materials leaves them out.
     */
    private List<SubAssembly> subAssemblies(WorkOrder parent) {
        List<WorkOrder> children = workOrderRepository.findByParentWorkOrderIdAndDeletedDateIsNullOrderByIdAsc(parent.getId());
        if (children.isEmpty()) return List.of();

        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(parent.getId()).stream()
                .filter(m -> m.getDeletedDate() == null && m.getComponent() != null)
                .toList();
        List<SubAssembly> subAssemblies = new ArrayList<>();
        for (WorkOrder child : children) {
            if (child.getWorkOrderStatus() == WorkOrderStatus.CANCELLED) continue;
            if (Integer.valueOf(parent.getId()).equals(child.getSplitFromWorkOrderId())) continue;
            Set<Integer> outputs = outputItemIds(child);
            List<WorkOrderMaterial> used = materials.stream()
                    .filter(m -> outputs.contains(m.getComponent().getInventoryItemId()))
                    .toList();
            if (!used.isEmpty()) subAssemblies.add(new SubAssembly(child, used));
        }
        return subAssemblies;
    }

    /** The items an order makes: one per line, or its BOM's item when it has no lines. */
    private static Set<Integer> outputItemIds(WorkOrder workOrder) {
        Set<Integer> ids = new HashSet<>();
        for (WorkOrderLine line : workOrder.activeLines()) {
            if (line.getInventoryItem() != null) ids.add(line.getInventoryItem().getInventoryItemId());
            else if (line.getBom() != null && line.getBom().getParentInventoryItem() != null) {
                ids.add(line.getBom().getParentInventoryItem().getInventoryItemId());
            }
        }
        if (ids.isEmpty() && workOrder.getBom() != null && workOrder.getBom().getParentInventoryItem() != null) {
            ids.add(workOrder.getBom().getParentInventoryItem().getInventoryItemId());
        }
        return ids;
    }

    /**
     * The operations that use a material: the one it is issued to, or else the first
     * operations of the order — of its line, when the material belongs to one.
     */
    private static List<Long> consumers(WorkOrderMaterial material, List<WorkOrderOperation> operations,
                                        Map<Long, Set<Long>> predecessors) {
        WorkOrderOperation at = material.getWorkOrderOperation();
        if (at != null && operations.stream().anyMatch(op -> op.getId().equals(at.getId()))) {
            return List.of(at.getId());
        }
        Long lineId = material.getWorkOrderLine() != null ? material.getWorkOrderLine().getId() : null;
        return operations.stream()
                .filter(op -> predecessors.get(op.getId()).isEmpty())
                .filter(op -> lineId == null || op.getWorkOrderLine() == null || lineId.equals(op.getWorkOrderLine().getId()))
                .map(WorkOrderOperation::getId)
                .toList();
    }

    /**
     * When an order's output is ready to move on: its last operation's end plus that
     * operation's move time. Taken from this run when the order was planned in it, otherwise
     * from its saved plan. Null when it has never been planned.
     */
    private LocalDateTime readyAt(WorkOrder workOrder, Ledger ledger) {
        LocalDateTime planned = ledger.readyAt.get(workOrder.getId());
        if (planned != null) return planned;

        List<WorkOrderOperation> operations = loadOperations(workOrder);
        if (operations.isEmpty()) {
            return workOrder.getPlannedEndDate() != null ? toLocal(workOrder.getPlannedEndDate()) : null;
        }
        LocalDateTime ready = null;
        for (WorkOrderOperation op : operations) {
            boolean done = op.getStatus() == OperationStatus.COMPLETED;
            Date end = done && op.getActualEndDate() != null ? op.getActualEndDate() : op.getPlannedEndDate();
            if (end == null) {
                if (done) continue;
                return null;
            }
            LocalDateTime arrives = toLocal(end).plusMinutes(moveMinutes(op));
            ready = ready == null ? arrives : later(ready, arrives);
        }
        // Every operation done, none of them dated: the output is there now.
        return ready != null ? ready : ledger.now;
    }

    /**
     * When the parent first needs a sub-assembly: the earliest planned start among the
     * operations that use it and have not started yet.
     */
    private LocalDateTime neededAt(SubAssembly sub, List<WorkOrderOperation> parentOperations) {
        Map<Long, Set<Long>> predecessors = predecessors(parentOperations);
        Map<Long, WorkOrderOperation> byId = parentOperations.stream()
                .collect(Collectors.toMap(WorkOrderOperation::getId, op -> op));
        LocalDateTime needed = null;
        for (WorkOrderMaterial material : sub.consumedBy()) {
            for (Long opId : consumers(material, parentOperations, predecessors)) {
                WorkOrderOperation op = byId.get(opId);
                if (op.getPlannedStartDate() == null || STARTED_OPERATIONS.contains(op.getStatus())) continue;
                LocalDateTime start = toLocal(op.getPlannedStartDate());
                needed = needed == null ? start : earlier(needed, start);
            }
        }
        return needed;
    }

    /**
     * Warns when this order, as now planned, makes its output later than the order it is a
     * sub-assembly of plans to use it. A parent planned later in the same run waits for it by
     * itself, so it is not checked.
     */
    private void checkParentNeeds(WorkOrder child, LocalDateTime ready, Ledger ledger, List<String> warnings) {
        WorkOrder parent = child.getParentWorkOrder();
        if (parent == null || parent.getDeletedDate() != null
                || FINISHED_WORK_ORDERS.contains(parent.getWorkOrderStatus())) return;
        if (ledger.batch.contains(parent.getId()) && !ledger.readyAt.containsKey(parent.getId())) return;

        for (SubAssembly sub : subAssemblies(parent)) {
            if (sub.child().getId() != child.getId()) continue;
            LocalDateTime needed = neededAt(sub, loadOperations(parent));
            if (needed != null && ready.isAfter(needed)) {
                warnings.add(parent.getWorkOrderNumber() + " needs this order's output at " + fmt(needed)
                        + ", but it will be ready only at " + fmt(ready) + ". Reschedule "
                        + parent.getWorkOrderNumber() + " so it waits for it.");
            }
        }
    }

    /** The longest move time after an operation nothing else follows: the last leg to the parent. */
    private long finalMoveMinutes(List<WorkOrderOperation> operations) {
        Set<Long> followed = new HashSet<>();
        predecessors(operations).values().forEach(followed::addAll);
        return operations.stream().filter(op -> !followed.contains(op.getId()))
                .mapToLong(ProductionSchedulerService::moveMinutes).max().orElse(0);
    }

    /**
     * How deep an order sits among the batch's sub-assemblies: 0 when it uses none of them,
     * otherwise one more than the deepest it uses. A loop in the parent links counts as 0.
     */
    private static int level(int workOrderId, Map<Integer, List<SubAssembly>> subAssemblies,
                             Map<Integer, Integer> levels, Set<Integer> visiting) {
        Integer known = levels.get(workOrderId);
        if (known != null) return known;
        if (!visiting.add(workOrderId)) return 0;
        int level = 0;
        for (SubAssembly sub : subAssemblies.getOrDefault(workOrderId, List.of())) {
            level = Math.max(level, 1 + level(sub.child().getId(), subAssemblies, levels, visiting));
        }
        visiting.remove(workOrderId);
        levels.put(workOrderId, level);
        return level;
    }

    // ──────────────────────────────── work already under way ────────────────────────────────

    /**
     * The operations of this order that are not planned but held where they are: completed,
     * running, or on hold. Running and on-hold ones are booked into {@code draft} first, so the
     * rest of the order plans around them.
     */
    private Map<Long, Placement> pin(WorkOrder workOrder, List<WorkOrderOperation> operations, Ledger ledger,
                                     Map<String, ResourceTimeline> draft, List<String> warnings) {
        Map<Long, Placement> pinned = new HashMap<>();
        for (WorkOrderOperation op : operations) {
            Placement held = op.getStatus() == null ? null : switch (op.getStatus()) {
                case COMPLETED -> pinCompleted(workOrder, op, ledger);
                case IN_PROGRESS -> pinInProgress(workOrder, op, ledger, draft, warnings);
                case HOLD -> pinOnHold(workOrder, op, ledger, draft, warnings);
                default -> null;
            };
            if (held != null) pinned.put(op.getId(), held);
        }
        return pinned;
    }

    /** Done: it books nothing, and hands over when it actually ended. */
    private Placement pinCompleted(WorkOrder workOrder, WorkOrderOperation op, Ledger ledger) {
        LocalDateTime end = firstOf(op.getActualEndDate(), op.getPlannedEndDate(), ledger.now);
        LocalDateTime start = earlier(firstOf(op.getActualStartDate(), op.getPlannedStartDate(), end), end);
        WorkCenter wc = workCenterOf(workOrder, op);
        return new Placement(op, wc, op.getAssignedMachine(), calendarOf(wc, ledger), new Slot(start, end),
                durationMinutes(workOrder, op), end, Pin.COMPLETED);
    }

    /**
     * Running: it stays on its machine from when it started, and holds it until its remaining
     * quantity is made. It is booked even over something else — it is physically on the machine
     * — and the clash is reported.
     */
    private Placement pinInProgress(WorkOrder workOrder, WorkOrderOperation op, Ledger ledger,
                                    Map<String, ResourceTimeline> draft, List<String> warnings) {
        Station station = station(workOrder, op, ledger, warnings);
        MachineDetails machine = op.getAssignedMachine();
        if (machine == null) {
            List<MachineDetails> machines = candidateMachines(op, station.workCenter(), warnings);
            if (!machines.isEmpty()) {
                machine = machines.get(0);
                warnings.add("Operation " + op.getSequence() + " (" + op.getOperationName()
                        + ") is running with no machine recorded; it was booked on " + machine.getMachineCode() + ".");
            }
        }

        LocalDateTime now = ledger.now;
        LocalDateTime end = remainingWorkEnd(workOrder, op, station.calendar(), now);
        LocalDateTime start = earlier(firstOf(op.getActualStartDate(), op.getPlannedStartDate(), now), now);

        ResourceTimeline timeline = timeline(resourceKey(machine, station.workCenter()), machine, ledger, draft, true);
        if (timeline.isBusyDuring(now, end)) {
            warnings.add("Operation " + op.getSequence() + " (" + op.getOperationName() + ") is still running on "
                    + (machine != null ? machine.getMachineCode() : station.workCenter().getCenterCode())
                    + " until " + end + ", over time already booked there for other work.");
        }
        timeline.block(now, end);

        // With transfer batches its output may already be on its way, or will be after the next batch.
        LocalDateTime handOver = end;
        BigDecimal batch = transferBatch(workOrder, op);
        if (batch != null) {
            BigDecimal toFirstBatch = batch.subtract(doneQuantity(op)).max(BigDecimal.ZERO);
            LocalDateTime ready = station.calendar()
                    .advance(now, runPerUnit(op).multiply(toFirstBatch).doubleValue(), MAX_SCHEDULING_HORIZON_DAYS)
                    .orElse(end);
            handOver = earlier(ready, end);
        }
        return new Placement(op, station.workCenter(), machine, station.calendar(), new Slot(start, end),
                durationMinutes(workOrder, op), handOver, Pin.IN_PROGRESS);
    }

    /**
     * On hold: it keeps its planned dates and machine, stays booked, and its successors wait
     * for it. One that has never been planned has no dates to keep, so it is planned like the
     * rest.
     */
    private Placement pinOnHold(WorkOrder workOrder, WorkOrderOperation op, Ledger ledger,
                                Map<String, ResourceTimeline> draft, List<String> warnings) {
        if (op.getPlannedStartDate() == null || op.getPlannedEndDate() == null) {
            warnings.add("Operation " + op.getSequence() + " (" + op.getOperationName()
                    + ") is on hold but was never planned, so it was planned with the rest.");
            return null;
        }
        Slot slot = new Slot(toLocal(op.getPlannedStartDate()), toLocal(op.getPlannedEndDate()));
        WorkCenter wc = workCenterOf(workOrder, op);
        WorkCalendar calendar = calendarOf(wc, ledger);
        if (wc != null || op.getAssignedMachine() != null) {
            timeline(resourceKey(op.getAssignedMachine(), wc), op.getAssignedMachine(), ledger, draft, true)
                    .block(slot.start(), slot.end());
        }
        warnings.add("Operation " + op.getSequence() + " (" + op.getOperationName()
                + ") is on hold; it keeps " + slot.start() + " to " + slot.end() + " until it is moved.");
        LocalDateTime handOver = calendar != null && calendar.hasCapacity()
                ? handOverAt(workOrder, op, calendar, slot) : slot.end();
        return new Placement(op, wc, op.getAssignedMachine(), calendar, slot, durationMinutes(workOrder, op),
                handOver, Pin.HOLD);
    }

    /**
     * When a running operation will be done: the quantity it still has to make, at its run
     * rate, worked from {@code now}. Its setup is already behind it.
     */
    private LocalDateTime remainingWorkEnd(WorkOrder workOrder, WorkOrderOperation op, WorkCalendar calendar,
                                           LocalDateTime now) {
        BigDecimal left = quantity(workOrder, op).subtract(doneQuantity(op)).max(BigDecimal.ZERO);
        double work = runPerUnit(op).multiply(left).doubleValue();
        return calendar == null || !calendar.hasCapacity() ? now.plusMinutes((long) Math.ceil(work))
                : calendar.advance(now, work, MAX_SCHEDULING_HORIZON_DAYS).orElse(now);
    }

    /** What an operation has already turned out, good or not: none of it needs making again. */
    private static BigDecimal doneQuantity(WorkOrderOperation op) {
        return orZero(op.getCompletedQuantity()).add(orZero(op.getScrappedQuantity())).add(orZero(op.getRejectedQuantity()));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static WorkCenter workCenterOf(WorkOrder workOrder, WorkOrderOperation op) {
        return op.getWorkCenter() != null ? op.getWorkCenter() : workOrder.getWorkCenter();
    }

    private static WorkCalendar calendarOf(WorkCenter wc, Ledger ledger) {
        return wc == null ? null : ledger.calendars.computeIfAbsent(wc.getId(), id -> WorkCalendarFactory.from(wc));
    }

    private static LocalDateTime firstOf(Date first, Date second, LocalDateTime otherwise) {
        return first != null ? toLocal(first) : second != null ? toLocal(second) : otherwise;
    }

    /** The machines to try. A null entry means the work centre itself is the resource. */
    private List<MachineDetails> candidates(WorkOrderOperation op, WorkCenter wc, List<String> warnings) {
        List<MachineDetails> machines = candidateMachines(op, wc, warnings);
        return machines.isEmpty() ? Collections.singletonList(null) : machines;
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

    // ──────────────────────────────── operation timing ────────────────────────────────

    /** Total time = setupTime + runTime × the operation's own planned quantity. */
    private BigDecimal durationMinutes(WorkOrder workOrder, WorkOrderOperation op) {
        return setupMinutes(op).add(runPerUnit(op).multiply(quantity(workOrder, op))).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal setupMinutes(WorkOrderOperation op) {
        return op.getRoutingOperation() != null && op.getRoutingOperation().getSetupTime() != null
                ? op.getRoutingOperation().getSetupTime() : BigDecimal.ZERO;
    }

    private static BigDecimal runPerUnit(WorkOrderOperation op) {
        return op.getRoutingOperation() != null && op.getRoutingOperation().getRunTime() != null
                ? op.getRoutingOperation().getRunTime() : BigDecimal.ZERO;
    }

    private static BigDecimal quantity(WorkOrder workOrder, WorkOrderOperation op) {
        return op.getPlannedQuantity() != null ? op.getPlannedQuantity()
                : workOrder.getPlannedQuantity() != null ? workOrder.getPlannedQuantity() : BigDecimal.ZERO;
    }

    private static long queueMinutes(WorkOrderOperation op) {
        return op.getRoutingOperation() == null ? 0 : wallMinutes(op.getRoutingOperation().getQueueTime());
    }

    private static long moveMinutes(WorkOrderOperation op) {
        return op.getRoutingOperation() == null ? 0 : wallMinutes(op.getRoutingOperation().getMoveTime());
    }

    private static long wallMinutes(BigDecimal minutes) {
        if (minutes == null || minutes.signum() <= 0) return 0;
        return minutes.setScale(0, RoundingMode.CEILING).longValue();
    }

    /** The transfer batch, or null when the whole quantity moves on at once. */
    private static BigDecimal transferBatch(WorkOrder workOrder, WorkOrderOperation op) {
        BigDecimal batch = op.getRoutingOperation() != null ? op.getRoutingOperation().getTransferQuantity() : null;
        if (batch == null || batch.signum() <= 0) return null;
        return batch.compareTo(quantity(workOrder, op)) < 0 ? batch : null;
    }

    /** When an operation in {@code slot} has made its first transfer batch. */
    private LocalDateTime handOverAt(WorkOrder workOrder, WorkOrderOperation op, WorkCalendar calendar, Slot slot) {
        BigDecimal batch = transferBatch(workOrder, op);
        if (batch == null) return slot.end();
        double firstBatchWork = setupMinutes(op).add(runPerUnit(op).multiply(batch)).doubleValue();
        LocalDateTime ready = calendar.advance(slot.start(), firstBatchWork, MAX_SCHEDULING_HORIZON_DAYS)
                .orElse(slot.end());
        return earlier(ready, slot.end());
    }

    // ──────────────────────────────── capacity ledger ────────────────────────────────

    /** What is booked on every resource, and each work centre's calendar, for one scheduling run. */
    private static class Ledger {
        final Map<String, ResourceTimeline> timelines = new HashMap<>();
        final Map<Integer, WorkCalendar> calendars = new HashMap<>();
        /** When each order planned in this run has its output ready to move on. */
        final Map<Integer, LocalDateTime> readyAt = new HashMap<>();
        /** The orders this run plans. */
        final Set<Integer> batch = new HashSet<>();
        final LocalDateTime now;

        Ledger(LocalDateTime now) {
            this.now = now;
        }
    }

    /**
     * Builds the ledger from live operations rather than a table of its own, so a booking
     * disappears the moment its operation is completed, cancelled or deleted. The work orders
     * being scheduled are left out: their old dates are about to be replaced, and their
     * running and on-hold operations are booked again when they are pinned.
     *
     * <p>A running operation holds its machine at least until its remaining quantity is made,
     * even when that is past its planned end: overrunning work is still on the machine.
     */
    private Ledger loadLedger(LocalDateTime now, Set<Integer> workOrdersBeingScheduled) {
        Ledger ledger = new Ledger(now);
        ledger.batch.addAll(workOrdersBeingScheduled);
        List<WorkOrderOperation> booked = workOrderOperationRepository.findBookedLoad(
                toDate(now), OperationStatus.IN_PROGRESS, FINISHED_OPERATIONS, FINISHED_WORK_ORDERS);

        for (WorkOrderOperation op : booked) {
            if (workOrdersBeingScheduled.contains(op.getWorkOrder().getId())) continue;
            if (op.getWorkCenter() == null && op.getAssignedMachine() == null) continue;

            LocalDateTime start = op.getPlannedStartDate() != null ? toLocal(op.getPlannedStartDate()) : now;
            LocalDateTime end = op.getPlannedEndDate() != null ? toLocal(op.getPlannedEndDate()) : now;
            if (op.getStatus() == OperationStatus.IN_PROGRESS) {
                start = earlier(start, now);
                end = later(end, remainingWorkEnd(op.getWorkOrder(), op, calendarOf(op.getWorkCenter(), ledger), now));
            }
            timeline(resourceKey(op.getAssignedMachine(), op.getWorkCenter()), op.getAssignedMachine(),
                    ledger, ledger.timelines, true)
                    .block(start, end);
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

    /**
     * Where a forward plan may begin. The work order's own start date counts only until it has
     * been scheduled: after that it holds the scheduler's last answer, not a request, and using
     * it would stop a later run from ever pulling the order earlier (a just-in-time plan could
     * not go back to as-soon-as-possible). Work cannot be planned into the past: a start date
     * that has gone by means "from now".
     */
    private LocalDateTime requestedStart(WorkOrder workOrder, Date overrideStartDate, LocalDateTime now) {
        Date requested = overrideStartDate != null ? overrideStartDate
                : workOrder.getScheduledAt() == null ? workOrder.getPlannedStartDate() : null;
        if (requested == null) return now;
        LocalDateTime start = toLocal(requested);
        return start.isBefore(now) ? now : start;
    }

    /** When the order is due. A due date entered as a plain date means "by the end of that day". */
    private static LocalDateTime dueBy(WorkOrder workOrder) {
        if (workOrder.getDueDate() == null) return null;
        LocalDateTime due = toLocal(workOrder.getDueDate());
        return due.toLocalTime().equals(LocalTime.MIDNIGHT) ? due.plusDays(1) : due;
    }

    private void checkDueDate(WorkOrder workOrder, Date woEnd, List<String> warnings) {
        LocalDateTime due = dueBy(workOrder);
        if (due == null) return;

        LocalDateTime end = toLocal(woEnd);
        if (!end.isAfter(due)) return;

        long lateMinutes = ChronoUnit.MINUTES.between(due, end);
        long diffDays = (lateMinutes + 24 * 60 - 1) / (24 * 60);
        warnings.add("Due date will be missed by " + diffDays + " day(s). "
                + "Scheduled end: " + woEnd + ", Due: " + workOrder.getDueDate());
    }

    private void logDecision(WorkOrder wo, Placement p, ScheduleDirection direction) {
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
        String how = p.pin() == Pin.COMPLETED ? "Kept completed"
                : p.pin() == Pin.IN_PROGRESS ? "Kept running"
                : p.pin() == Pin.HOLD ? "Kept on hold"
                : (direction == ScheduleDirection.BACKWARD ? "Backward" : "Forward") + " scheduling";
        log.setReason(how + " op " + op.getSequence()
                + " (" + op.getOperationName() + ")"
                + (p.workCenter() != null ? " at WC " + p.workCenter().getCenterCode() : "")
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

    /** A moment as the planner reads it, e.g. "24 Oct 2026 14:00". */
    private static String fmt(LocalDateTime dateTime) {
        return dateTime.format(WHEN);
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime toLocal(Date date) {
        // Not date.toInstant(): java.sql.Date refuses it, and a driver may hand one back.
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), IST).truncatedTo(ChronoUnit.MINUTES);
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(IST).toInstant());
    }
}
