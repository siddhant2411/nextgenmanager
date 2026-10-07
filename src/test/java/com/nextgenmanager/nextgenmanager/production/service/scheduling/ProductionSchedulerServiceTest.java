package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleResultDTO;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeEvent;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenterShift;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.DowntimeEventRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductionSchedulerServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Monday 5 Oct 2026, an hour before the 09:00 shift opens. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 8, 0);

    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private WorkOrderOperationRepository workOrderOperationRepository;
    @Mock
    private DowntimeEventRepository downtimeEventRepository;
    @Mock
    private WorkOrderAuditService auditService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    @InjectMocks
    private ProductionSchedulerService service;

    private long nextOperationId = 1;

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(NOW.atZone(IST).toInstant(), IST));
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(workOrderOperationRepository.findBookedLoad(any(), anyList(), anyList())).thenReturn(List.of());
        when(downtimeEventRepository.findByMachineIdAndEndTimeAfter(any(), any())).thenReturn(List.of());
    }

    @Test
    void operationsOnOneWorkCentreQueueUpInsteadOfSharingTheDay() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder wo = workOrder(10, "WO-10");
        // Three 8-hour operations: 480 minutes of run time for a quantity of one.
        operations(wo, operation(1, cutting, 480), operation(2, cutting, 480), operation(3, cutting, 480));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(result.getOperationSchedules())
                .extracting(s -> at(s.getPlannedStartDate()), s -> at(s.getPlannedEndDate()))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(day(5, 9, 0), day(5, 17, 0)),
                        org.assertj.core.groups.Tuple.tuple(day(6, 9, 0), day(6, 17, 0)),
                        org.assertj.core.groups.Tuple.tuple(day(7, 9, 0), day(7, 17, 0)));
        assertThat(wo.getWorkOrderStatus()).isEqualTo(WorkOrderStatus.SCHEDULED);
        assertThat(at(wo.getPlannedEndDate())).isEqualTo(day(7, 17, 0));
    }

    @Test
    void anOperationStartsTheMinuteItsPredecessorEndsOnAnotherWorkCentre() {
        WorkOrder wo = workOrder(10, "WO-10");
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 120), operation(2, workCenter(2, "WC-WELD"), 60));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(result.getOperationSchedules().get(1).getPlannedStartDate())).isEqualTo(day(5, 11, 0));
        assertThat(at(result.getOperationSchedules().get(1).getPlannedEndDate())).isEqualTo(day(5, 12, 0));
    }

    @Test
    void aWorkOrderPlansAroundWhatOtherWorkOrdersAlreadyBooked() {
        WorkCenter cutting = workCenter(1, "WC-CUT");

        WorkOrder other = workOrder(99, "WO-99");
        WorkOrderOperation booked = operation(1, cutting, 240);
        booked.setWorkOrder(other);
        booked.setPlannedStartDate(date(day(5, 9, 0)));
        booked.setPlannedEndDate(date(day(5, 13, 0)));
        when(workOrderOperationRepository.findBookedLoad(any(), anyList(), anyList())).thenReturn(List.of(booked));

        WorkOrder wo = workOrder(10, "WO-10");
        operations(wo, operation(1, cutting, 120));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(5, 13, 0));
        assertThat(at(result.getPlannedEndDate())).isEqualTo(day(5, 15, 0));
    }

    @Test
    void anUnassignedOperationTakesTheMachineThatFinishesSoonest() {
        WorkCenter cnc = workCenter(1, "WC-CNC");
        MachineDetails busy = machine(1L, "CNC-01", cnc);
        MachineDetails free = machine(2L, "CNC-02", cnc);
        cnc.setWorkStations(List.of(busy, free));

        WorkOrder other = workOrder(99, "WO-99");
        WorkOrderOperation booked = operation(1, cnc, 480);
        booked.setWorkOrder(other);
        booked.setAssignedMachine(busy);
        booked.setPlannedStartDate(date(day(5, 9, 0)));
        booked.setPlannedEndDate(date(day(5, 17, 0)));
        when(workOrderOperationRepository.findBookedLoad(any(), anyList(), anyList())).thenReturn(List.of(booked));

        WorkOrder wo = workOrder(10, "WO-10");
        WorkOrderOperation op = operation(1, cnc, 60);
        operations(wo, op);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(op.getAssignedMachine()).isSameAs(free);
        assertThat(result.getOperationSchedules().get(0).getMachineCode()).isEqualTo("CNC-02");
        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
    }

    @Test
    void plannedDowntimeOnAMachineIsNotScheduledOver() {
        WorkCenter cnc = workCenter(1, "WC-CNC");
        MachineDetails machine = machine(1L, "CNC-01", cnc);
        cnc.setWorkStations(List.of(machine));

        DowntimeEvent maintenance = new DowntimeEvent();
        maintenance.setStartTime(date(day(5, 9, 0)));
        maintenance.setEndTime(date(day(5, 12, 0)));
        when(downtimeEventRepository.findByMachineIdAndEndTimeAfter(any(), any())).thenReturn(List.of(maintenance));

        WorkOrder wo = workOrder(10, "WO-10");
        operations(wo, operation(1, cnc, 60));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(5, 12, 0));
    }

    @Test
    void durationComesFromTheOperationsOwnQuantityNotTheHeaders() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setPlannedQuantity(new BigDecimal("100"));
        WorkOrderOperation op = operation(1, workCenter(1, "WC-CUT"), 30);
        op.setPlannedQuantity(new BigDecimal("4"));
        operations(wo, op);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(result.getEstimatedProductionMinutes()).isEqualByComparingTo("120");
        assertThat(at(result.getPlannedEndDate())).isEqualTo(day(5, 11, 0));
    }

    @Test
    void independentPathsRunAtOnceAndTheJoinWaitsForBoth() {
        WorkOrder wo = workOrder(10, "WO-10");
        WorkOrderOperation machining = operation(1, workCenter(1, "WC-CNC"), 240);
        WorkOrderOperation painting = operation(2, workCenter(2, "WC-PAINT"), 60);
        WorkOrderOperation assembly = operation(3, workCenter(3, "WC-ASM"), 60);
        assembly.setDependsOnOperationIds(new HashSet<>(Set.of(machining.getId(), painting.getId())));
        operations(wo, machining, painting, assembly);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        List<ScheduleResultDTO.OperationSchedule> ops = result.getOperationSchedules();
        assertThat(at(ops.get(0).getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(ops.get(1).getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(ops.get(2).getPlannedStartDate())).isEqualTo(day(5, 13, 0));
    }

    @Test
    void finishingAfterTheDueDayRaisesTheWarningTheScreenLooksFor() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setDueDate(date(day(5, 0, 0)));
        operations(wo, operation(1, cutting, 480), operation(2, cutting, 480));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(result.getWarnings()).anyMatch(w -> w.startsWith("Due date will be missed by 1 day(s)."));
    }

    @Test
    void finishingDuringTheDueDayIsOnTime() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setDueDate(date(day(5, 0, 0)));
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 480));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void batchSchedulingGivesTheUrgentOrderTheFirstSlot() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder normal = workOrder(10, "WO-10");
        WorkOrder urgent = workOrder(11, "WO-11");
        urgent.setPriority(WorkOrderPriority.URGENT);
        operations(normal, operation(1, cutting, 480));
        operations(urgent, operation(1, cutting, 480));
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED))
                .thenReturn(new ArrayList<>(List.of(normal, urgent)));

        service.scheduleAll();

        assertThat(at(urgent.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(normal.getPlannedStartDate())).isEqualTo(day(6, 9, 0));
    }

    @Test
    void anOrderThatCannotBeScheduledLeavesNothingBookedForTheNext() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkCenter noShifts = workCenter(2, "WC-NONE");
        noShifts.setShifts(new ArrayList<>());

        WorkOrder broken = workOrder(10, "WO-10");
        broken.setPriority(WorkOrderPriority.URGENT);
        WorkOrderOperation firstOfBroken = operation(1, cutting, 480);
        operations(broken, firstOfBroken, operation(2, noShifts, 60));
        WorkOrder good = workOrder(11, "WO-11");
        operations(good, operation(1, cutting, 480));
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED))
                .thenReturn(new ArrayList<>(List.of(broken, good)));

        List<ScheduleResultDTO> results = service.scheduleAll();

        assertThat(results.get(0).getWarnings()).anyMatch(w -> w.startsWith("Scheduling failed:"));
        assertThat(firstOfBroken.getPlannedStartDate()).isNull();
        assertThat(broken.getWorkOrderStatus()).isEqualTo(WorkOrderStatus.CREATED);
        assertThat(at(good.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    private WorkOrder workOrder(int id, String number) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setWorkOrderNumber(number);
        wo.setWorkOrderStatus(WorkOrderStatus.CREATED);
        wo.setPlannedQuantity(BigDecimal.ONE);
        when(workOrderRepository.findById(id)).thenReturn(Optional.of(wo));
        return wo;
    }

    private void operations(WorkOrder wo, WorkOrderOperation... ops) {
        for (WorkOrderOperation op : ops) op.setWorkOrder(wo);
        when(workOrderOperationRepository.findByWorkOrderIdWithAssociationsOrderBySequence(wo.getId()))
                .thenReturn(Arrays.asList(ops));
    }

    /** An operation of {@code minutes} run time per unit, for a quantity of one. */
    private WorkOrderOperation operation(int sequence, WorkCenter workCenter, int minutes) {
        RoutingOperation routing = new RoutingOperation();
        routing.setSetupTime(BigDecimal.ZERO);
        routing.setRunTime(BigDecimal.valueOf(minutes));

        WorkOrderOperation op = new WorkOrderOperation();
        op.setId(nextOperationId++);
        op.setSequence(sequence);
        op.setOperationName("Op " + sequence);
        op.setWorkCenter(workCenter);
        op.setRoutingOperation(routing);
        op.setPlannedQuantity(BigDecimal.ONE);
        op.setStatus(OperationStatus.PLANNED);
        return op;
    }

    /** A work centre open 09:00–17:00 with no breaks, Monday to Saturday. */
    private WorkCenter workCenter(int id, String code) {
        WorkCenterShift shift = new WorkCenterShift();
        shift.setStartTime(LocalTime.of(9, 0));
        shift.setEndTime(LocalTime.of(17, 0));
        shift.setActiveDays(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY));
        shift.setBreakMinutes(0);
        shift.setPlannedCapacityMinutes(480);

        WorkCenter wc = new WorkCenter();
        wc.setId(id);
        wc.setCenterCode(code);
        wc.setShifts(new ArrayList<>(List.of(shift)));
        return wc;
    }

    private MachineDetails machine(Long id, String code, WorkCenter workCenter) {
        MachineDetails machine = new MachineDetails();
        machine.setId(id);
        machine.setMachineCode(code);
        machine.setWorkCenter(workCenter);
        machine.setMachineStatus(MachineDetails.MachineStatus.ACTIVE);
        return machine;
    }

    private static LocalDateTime day(int dayOfOctober, int hour, int minute) {
        return LocalDateTime.of(2026, 10, dayOfOctober, hour, minute);
    }

    private static Date date(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(IST).toInstant());
    }

    private static LocalDateTime at(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), IST);
    }
}
