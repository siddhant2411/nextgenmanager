package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleResultDTO;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeEvent;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenterShift;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.DowntimeEventRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.DispatchRule;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.MaterialLedger;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.ScheduleDirection;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private WorkOrderMaterialRepository workOrderMaterialRepository;
    @Mock
    private MaterialSupply materialSupply;
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

    /** What the scheduler is handed as the material ledger; each test fills it as it needs. */
    private MaterialLedger materialLedger = new MaterialLedger();

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(NOW.atZone(IST).toInstant(), IST));
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(workOrderOperationRepository.findBookedLoad(any(), any(), anyList(), anyList())).thenReturn(List.of());
        when(downtimeEventRepository.findByMachineIdAndEndTimeAfter(any(), any())).thenReturn(List.of());
        when(materialSupply.load(any(), any(), any())).thenAnswer(inv -> materialLedger);
        // Needs worked out the simple way: required less issued.
        when(materialSupply.needs(anyList())).thenAnswer(inv -> {
            List<WorkOrderMaterial> materials = inv.getArgument(0);
            List<MaterialSupply.Need> needs = new ArrayList<>();
            for (WorkOrderMaterial m : materials) {
                if (m.getNetRequiredQuantity() == null) continue;
                double left = m.getNetRequiredQuantity().subtract(m.getIssuedQuantity()).doubleValue();
                if (left > 0) needs.add(new MaterialSupply.Need(m, m.getComponent(), left));
            }
            return needs;
        });
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
        when(workOrderOperationRepository.findBookedLoad(any(), any(), anyList(), anyList())).thenReturn(List.of(booked));

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
        when(workOrderOperationRepository.findBookedLoad(any(), any(), anyList(), anyList())).thenReturn(List.of(booked));

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

    @Test
    void batchSchedulingByEarliestDueDateIgnoresPriority() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder dueSoon = workOrder(10, "WO-10");
        dueSoon.setDueDate(date(day(6, 0, 0)));
        WorkOrder urgentLater = workOrder(11, "WO-11");
        urgentLater.setPriority(WorkOrderPriority.URGENT);
        urgentLater.setDueDate(date(day(20, 0, 0)));
        operations(dueSoon, operation(1, cutting, 480));
        operations(urgentLater, operation(1, cutting, 480));
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED))
                .thenReturn(new ArrayList<>(List.of(urgentLater, dueSoon)));

        service.scheduleAll(DispatchRule.EDD, ScheduleDirection.FORWARD);

        assertThat(at(dueSoon.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(urgentLater.getPlannedStartDate())).isEqualTo(day(6, 9, 0));
    }

    // ─── backward ─────────────────────────────────────────────────────────────

    @Test
    void backwardTheLastOperationEndsAtTheCloseOfTheDueDayAndEarlierOnesArePulledBack() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setDueDate(date(day(7, 0, 0)));
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 120), operation(2, workCenter(2, "WC-WELD"), 60));

        ScheduleResultDTO result = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);

        assertThat(result.getDirection()).isEqualTo("BACKWARD");
        assertThat(result.getOperationSchedules())
                .extracting(s -> at(s.getPlannedStartDate()), s -> at(s.getPlannedEndDate()))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(day(7, 14, 0), day(7, 16, 0)),
                        org.assertj.core.groups.Tuple.tuple(day(7, 16, 0), day(7, 17, 0)));
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void backwardFallsBackToForwardWhenTheOrderCannotFinishInTime() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setDueDate(date(day(5, 0, 0)));
        operations(wo, operation(1, cutting, 480), operation(2, cutting, 480));

        ScheduleResultDTO result = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);

        assertThat(result.getDirection()).isEqualTo("FORWARD");
        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getWarnings())
                .anyMatch(w -> w.startsWith("Cannot finish by the due date working back from it"))
                .anyMatch(w -> w.startsWith("Due date will be missed by 1 day(s)."));
    }

    @Test
    void schedulingForwardAfterAJustInTimePlanPullsTheOrderBackToNow() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setDueDate(date(day(9, 0, 0)));
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 60));

        ScheduleResultDTO late = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);
        ScheduleResultDTO asap = service.scheduleWorkOrder(10, ScheduleDirection.FORWARD);

        assertThat(at(late.getPlannedStartDate())).isEqualTo(day(9, 16, 0));
        assertThat(at(asap.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
    }

    @Test
    void aStartDateEnteredBeforeTheFirstScheduleIsRespected() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setPlannedStartDate(date(day(7, 0, 0)));
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 60));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(7, 9, 0));
    }

    @Test
    void backwardWithoutADueDateSchedulesForward() {
        WorkOrder wo = workOrder(10, "WO-10");
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 60));

        ScheduleResultDTO result = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);

        assertThat(result.getDirection()).isEqualTo("FORWARD");
        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getWarnings()).anyMatch(w -> w.startsWith("There is no due date to schedule back from"));
    }

    // ─── time between operations ──────────────────────────────────────────────

    @Test
    void moveAndQueueTimeSitBetweenTwoOperations() {
        WorkOrder wo = workOrder(10, "WO-10");
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 120);
        cut.getRoutingOperation().setMoveTime(new BigDecimal("30"));
        WorkOrderOperation weld = operation(2, workCenter(2, "WC-WELD"), 60);
        weld.getRoutingOperation().setQueueTime(new BigDecimal("30"));
        operations(wo, cut, weld);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(result.getOperationSchedules().get(1).getPlannedStartDate())).isEqualTo(day(5, 12, 0));
        assertThat(at(result.getOperationSchedules().get(1).getPlannedEndDate())).isEqualTo(day(5, 13, 0));
    }

    @Test
    void aTransferBatchLetsTheNextOperationStartBeforeThisOneEnds() {
        WorkOrder wo = workOrder(10, "WO-10");
        // Ten pieces: cutting takes 12 min each and passes them on in twos; welding takes 6.
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 12);
        cut.setPlannedQuantity(BigDecimal.TEN);
        cut.getRoutingOperation().setTransferQuantity(new BigDecimal("2"));
        WorkOrderOperation weld = operation(2, workCenter(2, "WC-WELD"), 6);
        weld.setPlannedQuantity(BigDecimal.TEN);
        operations(wo, cut, weld);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        List<ScheduleResultDTO.OperationSchedule> ops = result.getOperationSchedules();
        assertThat(at(ops.get(0).getPlannedEndDate())).isEqualTo(day(5, 11, 0));
        // Welding could start at 09:24 with the first pair, but would then run dry; it starts
        // so that it finishes 12 minutes after the last pair arrives at 11:00.
        assertThat(at(ops.get(1).getPlannedStartDate())).isEqualTo(day(5, 10, 12));
        assertThat(at(ops.get(1).getPlannedEndDate())).isEqualTo(day(5, 11, 12));
    }

    @Test
    void backwardATransferBatchOverlapsThePredecessorWithItsSuccessor() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setDueDate(date(day(5, 0, 0)));
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 12);
        cut.setPlannedQuantity(BigDecimal.TEN);
        cut.getRoutingOperation().setTransferQuantity(new BigDecimal("2"));
        WorkOrderOperation weld = operation(2, workCenter(2, "WC-WELD"), 6);
        weld.setPlannedQuantity(BigDecimal.TEN);
        operations(wo, cut, weld);

        ScheduleResultDTO result = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);

        List<ScheduleResultDTO.OperationSchedule> ops = result.getOperationSchedules();
        assertThat(result.getDirection()).isEqualTo("BACKWARD");
        assertThat(at(ops.get(1).getPlannedStartDate())).isEqualTo(day(5, 16, 0));
        assertThat(at(ops.get(1).getPlannedEndDate())).isEqualTo(day(5, 17, 0));
        // The last pair must reach welding by 16:48 to be welded by 17:00.
        assertThat(at(ops.get(0).getPlannedStartDate())).isEqualTo(day(5, 14, 48));
        assertThat(at(ops.get(0).getPlannedEndDate())).isEqualTo(day(5, 16, 48));
    }

    // ─── work already under way ───────────────────────────────────────────────

    @Test
    void aCompletedOperationIsNotMovedAndTheStartedOrderKeepsItsStatus() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
        WorkOrderOperation done = operation(1, cutting, 180);
        done.setStatus(OperationStatus.COMPLETED);
        done.setPlannedStartDate(date(day(2, 9, 0)));
        done.setPlannedEndDate(date(day(2, 12, 0)));
        done.setActualStartDate(date(day(2, 9, 30)));
        done.setActualEndDate(date(day(2, 13, 0)));
        WorkOrderOperation next = operation(2, cutting, 60);
        operations(wo, done, next);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        verify(workOrderOperationRepository, never()).save(done);
        assertThat(at(done.getPlannedStartDate())).isEqualTo(day(2, 9, 0));
        assertThat(at(done.getPlannedEndDate())).isEqualTo(day(2, 12, 0));
        assertThat(result.getOperationSchedules().get(0).isPinned()).isTrue();
        assertThat(result.getOperationSchedules().get(0).getStatus()).isEqualTo("COMPLETED");
        // The finished work books nothing: the next operation takes the shift from its start.
        assertThat(at(next.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getOperationSchedules().get(1).isPinned()).isFalse();
        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(2, 9, 30));
        assertThat(wo.getWorkOrderStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void aRunningOperationKeepsItsMachineAndEndsWhenItsRemainingQuantityIsMade() {
        service.setClock(Clock.fixed(day(5, 10, 0).atZone(IST).toInstant(), IST));
        WorkCenter cnc = workCenter(1, "WC-CNC");
        MachineDetails running = machine(1L, "CNC-01", cnc);
        MachineDetails idle = machine(2L, "CNC-02", cnc);
        cnc.setWorkStations(List.of(running, idle));

        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
        WorkOrderOperation turning = operation(1, cnc, 10);
        turning.setPlannedQuantity(BigDecimal.TEN);
        turning.setStatus(OperationStatus.IN_PROGRESS);
        turning.setAssignedMachine(running);
        turning.setActualStartDate(date(day(5, 9, 0)));
        turning.setPlannedStartDate(date(day(5, 9, 0)));
        turning.setPlannedEndDate(date(day(5, 10, 40)));
        turning.setCompletedQuantity(new BigDecimal("3"));
        turning.setScrappedQuantity(BigDecimal.ONE);
        WorkOrderOperation inspect = operation(2, workCenter(2, "WC-QC"), 30);
        operations(wo, turning, inspect);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        // Six still to make at 10 minutes each, from 10:00.
        assertThat(turning.getAssignedMachine()).isSameAs(running);
        assertThat(at(turning.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(turning.getPlannedEndDate())).isEqualTo(day(5, 11, 0));
        assertThat(at(inspect.getPlannedStartDate())).isEqualTo(day(5, 11, 0));
        assertThat(result.getOperationSchedules().get(0).isPinned()).isTrue();
    }

    @Test
    void anOperationStartedEarlierThanPlannedIsPlannedFromWhenItStarted() {
        service.setClock(Clock.fixed(day(5, 10, 0).atZone(IST).toInstant(), IST));
        WorkCenter cnc = workCenter(1, "WC-CNC");
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
        WorkOrderOperation turning = operation(1, cnc, 60);
        turning.setStatus(OperationStatus.IN_PROGRESS);
        turning.setActualStartDate(date(day(5, 9, 30)));
        // Planned for Thursday, but the operator started it on Monday morning.
        turning.setPlannedStartDate(date(day(8, 9, 0)));
        turning.setPlannedEndDate(date(day(8, 10, 0)));
        operations(wo, turning);

        service.scheduleWorkOrder(10);

        assertThat(at(turning.getPlannedStartDate())).isEqualTo(day(5, 9, 30));
        assertThat(at(turning.getPlannedEndDate())).isEqualTo(day(5, 11, 0));
    }

    @Test
    void anotherOrdersOverrunningOperationStillHoldsItsMachine() {
        service.setClock(Clock.fixed(day(5, 10, 0).atZone(IST).toInstant(), IST));
        WorkCenter cutting = workCenter(1, "WC-CUT");

        WorkOrder other = workOrder(99, "WO-99");
        WorkOrderOperation overrunning = operation(1, cutting, 180);
        overrunning.setWorkOrder(other);
        overrunning.setStatus(OperationStatus.IN_PROGRESS);
        // Planned to end at 10:00, but nothing has been made yet.
        overrunning.setPlannedStartDate(date(day(5, 7, 0)));
        overrunning.setPlannedEndDate(date(day(5, 10, 0)));
        when(workOrderOperationRepository.findBookedLoad(any(), any(), anyList(), anyList()))
                .thenReturn(List.of(overrunning));

        WorkOrder wo = workOrder(10, "WO-10");
        operations(wo, operation(1, cutting, 60));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(result.getPlannedStartDate())).isEqualTo(day(5, 13, 0));
    }

    @Test
    void aRunningOperationBookedOverOtherWorkIsReportedNotMoved() {
        service.setClock(Clock.fixed(day(5, 10, 0).atZone(IST).toInstant(), IST));
        WorkCenter cutting = workCenter(1, "WC-CUT");

        WorkOrder other = workOrder(99, "WO-99");
        WorkOrderOperation booked = operation(1, cutting, 60);
        booked.setWorkOrder(other);
        booked.setPlannedStartDate(date(day(5, 10, 30)));
        booked.setPlannedEndDate(date(day(5, 11, 30)));
        when(workOrderOperationRepository.findBookedLoad(any(), any(), anyList(), anyList()))
                .thenReturn(List.of(booked));

        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
        WorkOrderOperation running = operation(1, cutting, 120);
        running.setStatus(OperationStatus.IN_PROGRESS);
        running.setActualStartDate(date(day(5, 9, 0)));
        operations(wo, running);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(running.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(running.getPlannedEndDate())).isEqualTo(day(5, 12, 0));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("still running") && w.contains("WC-CUT"));
    }

    @Test
    void anOperationOnHoldKeepsItsSlotAndItsSuccessorWaitsForIt() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.HOLD);
        WorkOrderOperation held = operation(1, cutting, 120);
        held.setStatus(OperationStatus.HOLD);
        held.setPlannedStartDate(date(day(6, 9, 0)));
        held.setPlannedEndDate(date(day(6, 11, 0)));
        WorkOrderOperation next = operation(2, workCenter(2, "WC-WELD"), 60);
        // Ten hours: from Monday 09:00 it would run into Tuesday morning, across the held slot.
        WorkOrderOperation parallel = operation(1, cutting, 600);
        next.setDependsOnOperationIds(new HashSet<>(Set.of(held.getId())));
        operations(wo, held, parallel, next);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        verify(workOrderOperationRepository, never()).save(held);
        assertThat(at(held.getPlannedStartDate())).isEqualTo(day(6, 9, 0));
        assertThat(at(next.getPlannedStartDate())).isEqualTo(day(6, 11, 0));
        // The held slot stays booked, so the long job waits until it is over.
        assertThat(at(parallel.getPlannedStartDate())).isEqualTo(day(6, 11, 0));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("on hold"));
        assertThat(wo.getWorkOrderStatus()).isEqualTo(WorkOrderStatus.HOLD);
    }

    @Test
    void anOperationOnHoldThatWasNeverPlannedIsPlannedWithTheRest() {
        WorkOrder wo = workOrder(10, "WO-10");
        WorkOrderOperation held = operation(1, workCenter(1, "WC-CUT"), 60);
        held.setStatus(OperationStatus.HOLD);
        operations(wo, held);

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(held.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getOperationSchedules().get(0).isPinned()).isFalse();
    }

    @Test
    void backwardPlansOnlyWhatIsLeftOfAStartedOrder() {
        WorkCenter cutting = workCenter(1, "WC-CUT");
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
        wo.setDueDate(date(day(7, 0, 0)));
        WorkOrderOperation done = operation(1, cutting, 60);
        done.setStatus(OperationStatus.COMPLETED);
        done.setActualStartDate(date(day(2, 9, 0)));
        done.setActualEndDate(date(day(2, 10, 0)));
        WorkOrderOperation next = operation(2, cutting, 60);
        operations(wo, done, next);

        ScheduleResultDTO result = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);

        assertThat(result.getDirection()).isEqualTo("BACKWARD");
        assertThat(at(next.getPlannedEndDate())).isEqualTo(day(7, 17, 0));
        verify(workOrderOperationRepository, never()).save(done);
    }

    @Test
    void aMaterialStateOrderCanBeScheduledAndKeepsItsStatus() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.MATERIAL_PENDING);
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 60));

        service.scheduleWorkOrder(10);

        assertThat(wo.getWorkOrderStatus()).isEqualTo(WorkOrderStatus.MATERIAL_PENDING);
        assertThat(wo.getScheduledAt()).isNotNull();
    }

    @Test
    void aFinishedOrderCannotBeScheduled() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setWorkOrderStatus(WorkOrderStatus.COMPLETED);
        operations(wo, operation(1, workCenter(1, "WC-CUT"), 60));

        assertThatThrownBy(() -> service.scheduleWorkOrder(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
    }

    // ─── sub-assemblies ───────────────────────────────────────────────────────

    @Test
    void theOperationThatUsesASubAssemblyWaitsUntilItIsReady() {
        WorkOrder parent = workOrder(10, "WO-10");
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 60);
        WorkOrderOperation assemble = operation(2, workCenter(2, "WC-ASM"), 60);
        operations(parent, cut, assemble);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        WorkOrderOperation weld = operation(1, workCenter(3, "WC-WELD"), 60);
        weld.setPlannedStartDate(date(day(6, 11, 0)));
        weld.setPlannedEndDate(date(day(6, 12, 0)));
        weld.getRoutingOperation().setMoveTime(BigDecimal.valueOf(30));
        operations(child, weld);
        materials(parent, material(parent, bracket, assemble));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        // Cutting does not need the bracket; assembly waits for it to be welded and moved.
        assertThat(at(cut.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(assemble.getPlannedStartDate())).isEqualTo(day(6, 12, 30));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("waits for sub-assembly WO-20")
                && w.contains("6 Oct 2026 12:30"));
    }

    @Test
    void aSubAssemblyMaterialNotTiedToAnOperationHoldsTheFirstOperation() {
        WorkOrder parent = workOrder(10, "WO-10");
        WorkOrderOperation first = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, first);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        WorkOrderOperation weld = operation(1, workCenter(3, "WC-WELD"), 60);
        weld.setPlannedStartDate(date(day(6, 11, 0)));
        weld.setPlannedEndDate(date(day(6, 12, 0)));
        operations(child, weld);
        materials(parent, material(parent, bracket, null));

        service.scheduleWorkOrder(10);

        assertThat(at(first.getPlannedStartDate())).isEqualTo(day(6, 12, 0));
    }

    @Test
    void aSplitOfTheOrderDoesNotHoldItUp() {
        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder parent = workOrder(10, "WO-10");
        WorkOrderOperation first = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, first);

        WorkOrder split = subAssembly(20, "WO-20", parent, bracket);
        split.setSplitFromWorkOrderId(10);
        WorkOrderOperation splitOp = operation(1, workCenter(3, "WC-WELD"), 60);
        splitOp.setPlannedStartDate(date(day(9, 9, 0)));
        splitOp.setPlannedEndDate(date(day(9, 10, 0)));
        operations(split, splitOp);
        materials(parent, material(parent, bracket, null));

        service.scheduleWorkOrder(10);

        assertThat(at(first.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
    }

    @Test
    void aChildOrderMakingSomethingTheParentDoesNotUseDoesNotHoldItUp() {
        WorkOrder parent = workOrder(10, "WO-10");
        WorkOrderOperation first = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, first);

        // A rework order makes the parent's own item, which the parent does not consume.
        WorkOrder rework = subAssembly(20, "WO-20", parent, item(900, "FG-VALVE"));
        WorkOrderOperation reworkOp = operation(1, workCenter(3, "WC-WELD"), 60);
        reworkOp.setPlannedStartDate(date(day(9, 9, 0)));
        reworkOp.setPlannedEndDate(date(day(9, 10, 0)));
        operations(rework, reworkOp);
        materials(parent, material(parent, item(500, "SA-BRACKET"), null));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(first.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getWarnings()).noneMatch(w -> w.contains("WO-20"));
    }

    @Test
    void anUnscheduledSubAssemblyIsReportedAndNotWaitedFor() {
        WorkOrder parent = workOrder(10, "WO-10");
        WorkOrderOperation first = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, first);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        operations(child, operation(1, workCenter(3, "WC-WELD"), 60));
        materials(parent, material(parent, bracket, null));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(first.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("WO-20 is not scheduled yet"));
    }

    @Test
    void aFinishedSubAssemblyIsAlreadyInStock() {
        WorkOrder parent = workOrder(10, "WO-10");
        WorkOrderOperation first = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, first);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        child.setWorkOrderStatus(WorkOrderStatus.COMPLETED);
        operations(child, operation(1, workCenter(3, "WC-WELD"), 60));
        materials(parent, material(parent, bracket, null));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(first.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void backwardThatWouldStartBeforeTheSubAssemblyIsReadyFallsBackToForward() {
        WorkOrder parent = workOrder(10, "WO-10");
        parent.setDueDate(date(day(7, 0, 0)));
        WorkOrderOperation assemble = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, assemble);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        WorkOrderOperation weld = operation(1, workCenter(3, "WC-WELD"), 60);
        weld.setPlannedStartDate(date(day(7, 15, 30)));
        weld.setPlannedEndDate(date(day(7, 16, 30)));
        operations(child, weld);
        materials(parent, material(parent, bracket, assemble));

        ScheduleResultDTO result = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);

        assertThat(result.getDirection()).isEqualTo("FORWARD");
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("before sub-assembly WO-20 is ready"));
        assertThat(at(assemble.getPlannedStartDate())).isEqualTo(day(7, 16, 30));
    }

    @Test
    void reschedulingASubAssemblyPastWhenItsParentNeedsItIsReported() {
        WorkOrder parent = workOrder(10, "WO-10");
        parent.setWorkOrderStatus(WorkOrderStatus.SCHEDULED);
        WorkOrderOperation assemble = operation(1, workCenter(1, "WC-ASM"), 60);
        assemble.setPlannedStartDate(date(day(5, 10, 0)));
        assemble.setPlannedEndDate(date(day(5, 11, 0)));
        operations(parent, assemble);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        WorkOrderOperation weld = operation(1, workCenter(3, "WC-WELD"), 480);
        operations(child, weld);
        materials(parent, material(parent, bracket, assemble));

        ScheduleResultDTO result = service.scheduleWorkOrder(20);

        assertThat(result.getWarnings()).anyMatch(w -> w.startsWith("WO-10 needs this order's output at 5 Oct 2026 10:00")
                && w.contains("ready only at 5 Oct 2026 17:00"));
    }

    @Test
    void scheduleAllPlansASubAssemblyBeforeTheOrderThatUsesItWhateverItsPriority() {
        WorkOrder parent = workOrder(10, "WO-10");
        parent.setPriority(WorkOrderPriority.URGENT);
        WorkOrderOperation assemble = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, assemble);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        child.setPriority(WorkOrderPriority.LOW);
        child.setWorkOrderStatus(WorkOrderStatus.CREATED);
        WorkOrderOperation weld = operation(1, workCenter(3, "WC-WELD"), 120);
        operations(child, weld);
        materials(parent, material(parent, bracket, null));
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED)).thenReturn(List.of(parent, child));

        List<ScheduleResultDTO> results = service.scheduleAll(DispatchRule.PRIORITY, ScheduleDirection.FORWARD);

        assertThat(results).extracting(ScheduleResultDTO::getWorkOrderNumber).containsExactly("WO-20", "WO-10");
        assertThat(at(weld.getPlannedEndDate())).isEqualTo(day(5, 11, 0));
        assertThat(at(assemble.getPlannedStartDate())).isEqualTo(day(5, 11, 0));
    }

    @Test
    void scheduleAllBackwardMakesTheSubAssemblyDueWhenItsParentNeedsIt() {
        WorkOrder parent = workOrder(10, "WO-10");
        parent.setDueDate(date(day(7, 0, 0)));
        WorkOrderOperation assemble = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, assemble);

        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        child.setWorkOrderStatus(WorkOrderStatus.CREATED);
        WorkOrderOperation weld = operation(1, workCenter(3, "WC-WELD"), 120);
        weld.getRoutingOperation().setMoveTime(BigDecimal.valueOf(30));
        operations(child, weld);
        materials(parent, material(parent, bracket, null));
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED)).thenReturn(List.of(child, parent));

        List<ScheduleResultDTO> results = service.scheduleAll(DispatchRule.PRIORITY, ScheduleDirection.BACKWARD);

        assertThat(results).extracting(ScheduleResultDTO::getWorkOrderNumber).containsExactly("WO-10", "WO-20");
        assertThat(results).extracting(ScheduleResultDTO::getDirection).containsOnly("BACKWARD");
        // Assembly 16:00-17:00 on the due day; the bracket must be welded and moved by 16:00.
        assertThat(at(assemble.getPlannedStartDate())).isEqualTo(day(7, 16, 0));
        assertThat(at(weld.getPlannedEndDate())).isEqualTo(day(7, 15, 30));
        assertThat(results.get(1).getWarnings()).anyMatch(w -> w.contains("when WO-10 needs it"));
    }

    // ─── material ─────────────────────────────────────────────────────────────

    @Test
    void theOperationThatUsesMaterialOnOrderWaitsForTheOrderToArrive() {
        WorkOrder wo = workOrder(10, "WO-10");
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 60);
        WorkOrderOperation weld = operation(2, workCenter(2, "WC-WELD"), 60);
        operations(wo, cut, weld);
        InventoryItem rod = item(700, "RM-ROD");
        materials(wo, need(wo, rod, 50, weld));
        materialLedger.supply(700, NOW, 20, "stock").supply(700, day(7, 0, 0), 100, "PO/0031");

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        // Cutting needs no rod; welding waits for the PO, delivered Wednesday, and the 09:00 shift.
        assertThat(at(cut.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(weld.getPlannedStartDate())).isEqualTo(day(7, 9, 0));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("waits for material RM-ROD (PO/0031)"));
    }

    @Test
    void materialInStockDoesNotHoldAnythingUp() {
        WorkOrder wo = workOrder(10, "WO-10");
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 60);
        operations(wo, cut);
        InventoryItem rod = item(700, "RM-ROD");
        materials(wo, need(wo, rod, 50, null));
        materialLedger.supply(700, NOW, 50, "stock");

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(cut.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void materialThatCannotBeDatedIsReportedButNotWaitedFor() {
        WorkOrder wo = workOrder(10, "WO-10");
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 60);
        operations(wo, cut);
        materials(wo, need(wo, item(700, "RM-ROD"), 50, null));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(cut.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(result.getWarnings()).anyMatch(w -> w.startsWith("Not enough RM-ROD") && w.contains("no lead time"));
    }

    @Test
    void inABatchTheFirstOrderPlannedTakesTheStockAndTheNextWaitsForTheOrder() {
        InventoryItem rod = item(700, "RM-ROD");
        WorkOrder urgent = workOrder(10, "WO-10");
        urgent.setPriority(WorkOrderPriority.URGENT);
        WorkOrderOperation urgentCut = operation(1, workCenter(1, "WC-CUT"), 60);
        operations(urgent, urgentCut);
        materials(urgent, need(urgent, rod, 50, null));
        WorkOrder normal = workOrder(11, "WO-11");
        WorkOrderOperation normalCut = operation(1, workCenter(2, "WC-CUT2"), 60);
        operations(normal, normalCut);
        materials(normal, need(normal, rod, 50, null));
        materialLedger.supply(700, NOW, 60, "stock").supply(700, day(8, 0, 0), 100, "PO/0031");
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED)).thenReturn(List.of(normal, urgent));

        service.scheduleAll(DispatchRule.PRIORITY, ScheduleDirection.FORWARD);

        assertThat(at(urgentCut.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
        assertThat(at(normalCut.getPlannedStartDate())).isEqualTo(day(8, 9, 0));
    }

    @Test
    void anOrderThatFailsToScheduleLeavesItsMaterialForTheNext() {
        InventoryItem rod = item(700, "RM-ROD");
        WorkOrder failing = workOrder(10, "WO-10");
        failing.setPriority(WorkOrderPriority.URGENT);
        WorkCenter closed = workCenter(9, "WC-NONE");
        closed.setShifts(new ArrayList<>());
        operations(failing, operation(1, closed, 60));
        materials(failing, need(failing, rod, 50, null));
        WorkOrder next = workOrder(11, "WO-11");
        WorkOrderOperation nextCut = operation(1, workCenter(1, "WC-CUT"), 60);
        operations(next, nextCut);
        materials(next, need(next, rod, 50, null));
        materialLedger.supply(700, NOW, 50, "stock").supply(700, day(8, 0, 0), 100, "PO/0031");
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED)).thenReturn(List.of(failing, next));

        List<ScheduleResultDTO> results = service.scheduleAll(DispatchRule.PRIORITY, ScheduleDirection.FORWARD);

        assertThat(results.get(0).getWarnings()).anyMatch(w -> w.startsWith("Scheduling failed"));
        assertThat(at(nextCut.getPlannedStartDate())).isEqualTo(day(5, 9, 0));
    }

    @Test
    void materialMadeByASubAssemblyOrderIsWaitedForAsThatOrderNotAsStock() {
        WorkOrder parent = workOrder(10, "WO-10");
        WorkOrderOperation assemble = operation(1, workCenter(1, "WC-ASM"), 60);
        operations(parent, assemble);
        InventoryItem bracket = item(500, "SA-BRACKET");
        WorkOrder child = subAssembly(20, "WO-20", parent, bracket);
        WorkOrderOperation weld = operation(1, workCenter(3, "WC-WELD"), 60);
        weld.setPlannedStartDate(date(day(6, 11, 0)));
        weld.setPlannedEndDate(date(day(6, 12, 0)));
        operations(child, weld);
        materials(parent, need(parent, bracket, 10, null));

        ScheduleResultDTO result = service.scheduleWorkOrder(10);

        assertThat(at(assemble.getPlannedStartDate())).isEqualTo(day(6, 12, 0));
        assertThat(result.getWarnings()).noneMatch(w -> w.contains("Not enough SA-BRACKET"));
    }

    @Test
    void backwardDoesNotPlanAnOperationBeforeItsMaterialArrives() {
        WorkOrder wo = workOrder(10, "WO-10");
        wo.setDueDate(date(day(7, 0, 0)));
        WorkOrderOperation cut = operation(1, workCenter(1, "WC-CUT"), 60);
        operations(wo, cut);
        materials(wo, need(wo, item(700, "RM-ROD"), 50, null));
        materialLedger.supply(700, day(7, 16, 30), 100, "PO/0031");

        ScheduleResultDTO result = service.scheduleWorkOrder(10, ScheduleDirection.BACKWARD);

        assertThat(result.getDirection()).isEqualTo("FORWARD");
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("before material RM-ROD (PO/0031) is ready"));
        assertThat(at(cut.getPlannedStartDate())).isEqualTo(day(7, 16, 30));
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    private WorkOrderMaterial need(WorkOrder wo, InventoryItem component, double quantity, WorkOrderOperation usedAt) {
        WorkOrderMaterial material = material(wo, component, usedAt);
        material.setId(nextOperationId++);
        material.setNetRequiredQuantity(BigDecimal.valueOf(quantity));
        material.setIssuedQuantity(BigDecimal.ZERO);
        return material;
    }

    private InventoryItem item(int id, String code) {
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(id);
        item.setItemCode(code);
        return item;
    }

    /** A child order of {@code parent}, making {@code makes}, planned but not started. */
    private WorkOrder subAssembly(int id, String number, WorkOrder parent, InventoryItem makes) {
        WorkOrder child = workOrder(id, number);
        child.setWorkOrderStatus(WorkOrderStatus.SCHEDULED);
        child.setParentWorkOrder(parent);
        Bom bom = new Bom();
        bom.setParentInventoryItem(makes);
        child.setBom(bom);
        List<WorkOrder> siblings = new ArrayList<>(workOrderRepository.findByParentWorkOrderIdAndDeletedDateIsNullOrderByIdAsc(parent.getId()));
        siblings.add(child);
        when(workOrderRepository.findByParentWorkOrderIdAndDeletedDateIsNullOrderByIdAsc(parent.getId())).thenReturn(siblings);
        return child;
    }

    private WorkOrderMaterial material(WorkOrder workOrder, InventoryItem component, WorkOrderOperation usedAt) {
        WorkOrderMaterial material = new WorkOrderMaterial();
        material.setWorkOrder(workOrder);
        material.setComponent(component);
        material.setWorkOrderOperation(usedAt);
        return material;
    }

    private void materials(WorkOrder workOrder, WorkOrderMaterial... materials) {
        when(workOrderMaterialRepository.findByWorkOrderId(workOrder.getId())).thenReturn(Arrays.asList(materials));
    }

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
