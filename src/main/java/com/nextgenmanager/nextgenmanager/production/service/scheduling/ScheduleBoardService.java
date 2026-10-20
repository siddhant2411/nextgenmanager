package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO.Bar;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO.MachineAlert;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO.OrderAlert;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO.Resource;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO.Span;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeEvent;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.DowntimeEventRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.WorkCenterRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.TimeWindow;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.WorkCalendar;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.WorkCalendarFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Builds the planner board: every resource with when it works and how full it is, the
 * operations booked on it, and what a planner has to act on (late, at-risk and unscheduled
 * orders, overrunning work, machines down).
 *
 * <p>Working time comes from the same {@link WorkCalendar} the scheduler plans with, so the
 * board shows the capacity the plan was actually made against. Load is booked productive time
 * over available productive time.
 */
@Service
@Transactional(readOnly = true)
public class ScheduleBoardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Finishing within this much of the due date counts as at risk. */
    private static final long AT_RISK_MINUTES = 24 * 60;

    private static final List<WorkOrderStatus> FINISHED_WORK_ORDERS = List.of(
            WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED,
            WorkOrderStatus.CANCELLED, WorkOrderStatus.SHORT_CLOSED);

    private static final Set<OperationStatus> KEPT_OPERATIONS =
            EnumSet.of(OperationStatus.COMPLETED, OperationStatus.IN_PROGRESS, OperationStatus.HOLD);

    @Autowired
    private WorkCenterRepository workCenterRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private DowntimeEventRepository downtimeEventRepository;

    private Clock clock = Clock.system(IST);

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** The board from the start of {@code fromDay} to the end of {@code toDay}. */
    public ScheduleBoardDTO board(LocalDate fromDay, LocalDate toDay) {
        if (toDay.isBefore(fromDay)) throw new IllegalArgumentException("'to' is before 'from'");
        LocalDateTime from = fromDay.atStartOfDay();
        LocalDateTime to = toDay.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);

        ScheduleBoardDTO board = new ScheduleBoardDTO();
        board.setFrom(from);
        board.setTo(to);
        board.setNow(now);

        Map<String, Row> rows = new LinkedHashMap<>();
        List<WorkCenter> workCenters = new ArrayList<>(workCenterRepository.findAllActive());
        workCenters.sort(Comparator.comparing(WorkCenter::getCenterCode, Comparator.nullsLast(String::compareTo)));
        for (WorkCenter wc : workCenters) {
            List<MachineDetails> machines = machinesOf(wc);
            if (machines.isEmpty()) {
                rows.put(workCenterKey(wc), new Row(wc, null, false, from, to));
            } else {
                for (MachineDetails machine : machines) {
                    Row row = new Row(wc, machine, false, from, to);
                    addDowntime(row, machine, from, to);
                    rows.put(machineKey(machine), row);
                }
            }
        }

        List<WorkOrderOperation> operations = workOrderOperationRepository.findBoardOperations(
                toDate(from), toDate(to), OperationStatus.CANCELLED, FINISHED_WORK_ORDERS);
        List<WorkOrder> withDueDate = workOrderRepository.findOpenWithDueDate(FINISHED_WORK_ORDERS).stream()
                .filter(wo -> wo.getScheduledAt() != null).toList();
        Map<Integer, Long> lateness = new HashMap<>();
        withDueDate.forEach(wo -> lateness.put(wo.getId(),
                ChronoUnit.MINUTES.between(dueBy(wo), toLocal(wo.getPlannedEndDate()))));
        for (WorkOrderOperation op : operations) {
            Row row = rowFor(op, rows, from, to);
            if (row == null) continue;
            Bar bar = toBar(op, lateness);
            row.resource.getBars().add(bar);
            row.booked += row.productiveMinutes(later(bar.getStart(), from), earlier(bar.getEnd(), to));
        }

        List<Row> ordered = new ArrayList<>(rows.values());
        ordered.sort(Comparator
                .comparing((Row r) -> Objects.toString(r.resource.getWorkCenterCode(), ""))
                .thenComparing(r -> r.resource.isUnassigned())
                .thenComparing(r -> Objects.toString(r.resource.getMachineCode(), "")));

        ScheduleBoardDTO.Kpis kpis = board.getKpis();
        for (Row row : ordered) {
            Resource resource = row.resource;
            resource.setCapacityMinutes(Math.round(row.capacity));
            resource.setBookedMinutes(Math.round(row.booked));
            resource.setLoadPercent(percent(row.booked, row.capacity));
            board.getResources().add(resource);

            kpis.setCapacityMinutes(kpis.getCapacityMinutes() + resource.getCapacityMinutes());
            kpis.setBookedMinutes(kpis.getBookedMinutes() + resource.getBookedMinutes());
            kpis.setOperations(kpis.getOperations() + resource.getBars().size());
            if (!resource.isUnassigned()) {
                kpis.setResources(kpis.getResources() + 1);
                if (row.capacity > 0 && resource.getBars().isEmpty()) kpis.setIdleResources(kpis.getIdleResources() + 1);
            }
            machineAlert(row, now).ifPresent(board.getMachinesDown()::add);
        }
        kpis.setLoadPercent(percent(kpis.getBookedMinutes(), kpis.getCapacityMinutes()));

        orderAlerts(board, withDueDate);
        board.setOverrunning(overrunning(now, lateness));

        kpis.setLateOrders(board.getLateOrders().size());
        kpis.setAtRiskOrders(board.getAtRiskOrders().size());
        kpis.setUnscheduledOrders(board.getUnscheduledOrders().size());
        kpis.setMachinesDown(board.getMachinesDown().size());
        return board;
    }

    // ──────────────────────────────── resources ────────────────────────────────

    /** One row of the board, with the calendar its time is measured on. */
    private static final class Row {
        final Resource resource = new Resource();
        final WorkCalendar calendar;
        final double capacity;
        double booked;

        Row(WorkCenter wc, MachineDetails machine, boolean unassigned, LocalDateTime from, LocalDateTime to) {
            calendar = WorkCalendarFactory.from(wc);
            resource.setKey(machine != null ? machineKey(machine) : workCenterKey(wc));
            resource.setWorkCenterId(wc.getId());
            resource.setWorkCenterCode(wc.getCenterCode());
            resource.setWorkCenterName(wc.getCenterName());
            resource.setUnassigned(unassigned);
            if (machine != null) {
                resource.setMachineId(machine.getId());
                resource.setMachineCode(machine.getMachineCode());
                resource.setMachineName(machine.getMachineName());
                if (machine.getMachineStatus() != null) resource.setMachineStatus(machine.getMachineStatus().name());
            }
            resource.setOpenTime(openTime(calendar, from, to));
            // Operations with no machine are already counted against the machines' capacity.
            capacity = unassigned || !calendar.hasCapacity() ? 0 : calendar.productiveMinutesBetween(from, to);
        }

        double productiveMinutes(LocalDateTime start, LocalDateTime end) {
            if (!end.isAfter(start)) return 0;
            return calendar.hasCapacity() ? calendar.productiveMinutesBetween(start, end)
                    : ChronoUnit.MINUTES.between(start, end);
        }
    }

    /**
     * The row an operation is drawn on: its machine, or its work centre. An operation on a work
     * centre that has machines but none assigned gets an "unassigned" row of its own, so it is
     * neither hidden nor drawn over a machine's work.
     */
    private Row rowFor(WorkOrderOperation op, Map<String, Row> rows, LocalDateTime from, LocalDateTime to) {
        MachineDetails machine = op.getAssignedMachine();
        WorkCenter wc = op.getWorkCenter() != null ? op.getWorkCenter()
                : machine != null ? machine.getWorkCenter() : op.getWorkOrder().getWorkCenter();
        if (wc == null) return null;

        String key = machine != null ? machineKey(machine) : workCenterKey(wc);
        Row row = rows.get(key);
        if (row != null) return row;

        // A machine no longer in the work centre, a deleted work centre, or no machine at all.
        boolean unassigned = machine == null && !machinesOf(wc).isEmpty();
        row = new Row(wc, machine, unassigned, from, to);
        if (machine != null) addDowntime(row, machine, from, to);
        rows.put(key, row);
        return row;
    }

    private List<MachineDetails> machinesOf(WorkCenter wc) {
        if (wc.getWorkStations() == null) return List.of();
        return wc.getWorkStations().stream()
                .filter(m -> m.getDeletedDate() == null)
                .sorted(Comparator.comparing(MachineDetails::getMachineCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** When the calendar is working inside [from, to), with touching windows joined. */
    private static List<Span> openTime(WorkCalendar calendar, LocalDateTime from, LocalDateTime to) {
        List<Span> open = new ArrayList<>();
        for (LocalDate day = from.toLocalDate().minusDays(1); day.isBefore(to.toLocalDate()); day = day.plusDays(1)) {
            for (TimeWindow window : calendar.windowsOn(day)) {
                LocalDateTime start = later(window.start(), from);
                LocalDateTime end = earlier(window.end(), to);
                if (!end.isAfter(start)) continue;
                Span last = open.isEmpty() ? null : open.get(open.size() - 1);
                if (last != null && !start.isAfter(last.getEnd())) {
                    if (end.isAfter(last.getEnd())) last.setEnd(end);
                } else {
                    open.add(new Span(start, end, null));
                }
            }
        }
        return open;
    }

    private void addDowntime(Row row, MachineDetails machine, LocalDateTime from, LocalDateTime to) {
        for (DowntimeEvent event : downtimeEventRepository.findByMachineIdAndEndTimeAfter(machine.getId(), toDate(from))) {
            if (event.getStartTime() == null || event.getEndTime() == null) continue;
            LocalDateTime start = toLocal(event.getStartTime());
            if (!start.isBefore(to)) continue;
            row.resource.getDowntime().add(new Span(later(start, from), earlier(toLocal(event.getEndTime()), to),
                    downtimeReason(event)));
        }
    }

    private static String downtimeReason(DowntimeEvent event) {
        if (event.getReasonCode() != null && event.getReasonCode().getDescription() != null) {
            return event.getReasonCode().getDescription();
        }
        return event.getRemarks() != null ? event.getRemarks() : "Downtime";
    }

    /** A machine that is not active, or that is down right now. */
    private Optional<MachineAlert> machineAlert(Row row, LocalDateTime now) {
        Resource resource = row.resource;
        if (resource.getMachineId() == null) return Optional.empty();
        MachineAlert alert = new MachineAlert();
        alert.setMachineId(resource.getMachineId());
        alert.setMachineCode(resource.getMachineCode());
        alert.setMachineName(resource.getMachineName());
        alert.setWorkCenterCode(resource.getWorkCenterCode());
        alert.setStatus(resource.getMachineStatus());

        for (Span down : resource.getDowntime()) {
            if (!down.getStart().isAfter(now) && down.getEnd().isAfter(now)) {
                alert.setReason(down.getLabel());
                alert.setUntil(down.getEnd());
                return Optional.of(alert);
            }
        }
        if (resource.getMachineStatus() != null && !"ACTIVE".equals(resource.getMachineStatus())) {
            alert.setReason(resource.getMachineStatus().replace('_', ' ').toLowerCase());
            return Optional.of(alert);
        }
        return Optional.empty();
    }

    // ──────────────────────────────── operations ────────────────────────────────

    private Bar toBar(WorkOrderOperation op, Map<Integer, Long> lateness) {
        WorkOrder wo = op.getWorkOrder();
        Bar bar = new Bar();
        bar.setOperationId(op.getId());
        bar.setSequence(op.getSequence());
        bar.setOperationName(op.getOperationName());
        bar.setOperationStatus(op.getStatus() != null ? op.getStatus().name() : null);
        bar.setWorkOrderId(wo.getId());
        bar.setWorkOrderNumber(wo.getWorkOrderNumber());
        bar.setWorkOrderStatus(wo.getWorkOrderStatus() != null ? wo.getWorkOrderStatus().name() : null);
        bar.setPriority(wo.getPriority() != null ? wo.getPriority().name() : null);

        WorkOrder parent = wo.getParentWorkOrder();
        if (parent != null && !Integer.valueOf(parent.getId()).equals(wo.getSplitFromWorkOrderId())) {
            bar.setParentWorkOrderId(parent.getId());
            bar.setParentWorkOrderNumber(parent.getWorkOrderNumber());
        }

        InventoryItem item = op.getWorkOrderLine() != null && op.getWorkOrderLine().getInventoryItem() != null
                ? op.getWorkOrderLine().getInventoryItem() : itemOf(wo);
        if (item != null) {
            bar.setItemCode(item.getItemCode());
            bar.setItemName(item.getName());
        }
        if (op.getWorkCenter() != null) bar.setWorkCenterCode(op.getWorkCenter().getCenterCode());
        if (op.getAssignedMachine() != null) bar.setMachineCode(op.getAssignedMachine().getMachineCode());

        bar.setStart(toLocal(op.getPlannedStartDate()));
        bar.setEnd(toLocal(op.getPlannedEndDate()));
        if (op.getActualStartDate() != null) bar.setActualStart(toLocal(op.getActualStartDate()));
        bar.setWorkMinutes(workMinutes(op));
        bar.setPlannedQuantity(op.getPlannedQuantity());
        bar.setCompletedQuantity(op.getCompletedQuantity());
        bar.setDueBy(dueBy(wo));
        Long late = lateness.get(wo.getId());
        bar.setLate(late != null && late > 0);
        bar.setKept(op.getStatus() != null && KEPT_OPERATIONS.contains(op.getStatus()));
        return bar;
    }

    /** Running operations whose planned end has already gone by. */
    private List<Bar> overrunning(LocalDateTime now, Map<Integer, Long> lateness) {
        List<Bar> overrunning = new ArrayList<>();
        for (WorkOrderOperation op : workOrderOperationRepository.findBookedLoad(toDate(now), OperationStatus.IN_PROGRESS,
                List.of(OperationStatus.COMPLETED, OperationStatus.CANCELLED), FINISHED_WORK_ORDERS)) {
            if (op.getStatus() != OperationStatus.IN_PROGRESS || op.getPlannedEndDate() == null
                    || op.getPlannedStartDate() == null) continue;
            if (toLocal(op.getPlannedEndDate()).isBefore(now)) overrunning.add(toBar(op, lateness));
        }
        overrunning.sort(Comparator.comparing(Bar::getEnd));
        return overrunning;
    }

    private static BigDecimal workMinutes(WorkOrderOperation op) {
        if (op.getRoutingOperation() == null) return BigDecimal.ZERO;
        BigDecimal setup = op.getRoutingOperation().getSetupTime() != null ? op.getRoutingOperation().getSetupTime() : BigDecimal.ZERO;
        BigDecimal run = op.getRoutingOperation().getRunTime() != null ? op.getRoutingOperation().getRunTime() : BigDecimal.ZERO;
        BigDecimal quantity = op.getPlannedQuantity() != null ? op.getPlannedQuantity() : BigDecimal.ZERO;
        return setup.add(run.multiply(quantity)).setScale(0, RoundingMode.HALF_UP);
    }

    // ──────────────────────────────── orders ────────────────────────────────

    /**
     * Late and at-risk orders among {@code withDueDate} (scheduled, unfinished orders with a
     * due date: a typed-in end date is not a plan), and new orders not yet scheduled.
     */
    private void orderAlerts(ScheduleBoardDTO board, List<WorkOrder> withDueDate) {
        for (WorkOrder wo : withDueDate) {
            OrderAlert alert = orderAlert(wo);
            if (alert.getMinutesLate() > 0) board.getLateOrders().add(alert);
            else if (alert.getMinutesLate() > -AT_RISK_MINUTES) board.getAtRiskOrders().add(alert);
        }
        board.getLateOrders().sort(Comparator.comparingLong(OrderAlert::getMinutesLate).reversed());
        board.getAtRiskOrders().sort(Comparator.comparingLong(OrderAlert::getMinutesLate).reversed());

        for (WorkOrder wo : workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED)) {
            if (wo.getDeletedDate() != null || wo.getScheduledAt() != null) continue;
            board.getUnscheduledOrders().add(orderAlert(wo));
        }
        board.getUnscheduledOrders().sort(Comparator.comparing(OrderAlert::getDueBy, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private OrderAlert orderAlert(WorkOrder wo) {
        OrderAlert alert = new OrderAlert();
        alert.setWorkOrderId(wo.getId());
        alert.setWorkOrderNumber(wo.getWorkOrderNumber());
        alert.setStatus(wo.getWorkOrderStatus() != null ? wo.getWorkOrderStatus().name() : null);
        alert.setPriority(wo.getPriority() != null ? wo.getPriority().name() : null);
        InventoryItem item = itemOf(wo);
        if (item != null) {
            alert.setItemCode(item.getItemCode());
            alert.setItemName(item.getName());
        }
        alert.setDueBy(dueBy(wo));
        if (wo.getPlannedEndDate() != null) alert.setPlannedEnd(toLocal(wo.getPlannedEndDate()));
        if (alert.getDueBy() != null && alert.getPlannedEnd() != null) {
            alert.setMinutesLate(ChronoUnit.MINUTES.between(alert.getDueBy(), alert.getPlannedEnd()));
        }
        return alert;
    }

    private static InventoryItem itemOf(WorkOrder wo) {
        for (WorkOrderLine line : wo.activeLines()) {
            if (line.getInventoryItem() != null) return line.getInventoryItem();
        }
        return wo.getBom() != null ? wo.getBom().getParentInventoryItem() : null;
    }

    /** When the order is due. A due date entered as a plain date means "by the end of that day". */
    private static LocalDateTime dueBy(WorkOrder wo) {
        if (wo.getDueDate() == null) return null;
        LocalDateTime due = toLocal(wo.getDueDate());
        return due.toLocalTime().equals(LocalTime.MIDNIGHT) ? due.plusDays(1) : due;
    }

    // ──────────────────────────────── helpers ────────────────────────────────

    private static String machineKey(MachineDetails machine) {
        return "M:" + machine.getId();
    }

    private static String workCenterKey(WorkCenter wc) {
        return "W:" + wc.getId();
    }

    private static double percent(double part, double whole) {
        if (whole <= 0) return 0;
        return Math.round(part / whole * 1000) / 10.0;
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime toLocal(Date date) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), IST).truncatedTo(ChronoUnit.MINUTES);
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(IST).toInstant());
    }
}
