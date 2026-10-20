package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO;
import com.nextgenmanager.nextgenmanager.production.dto.ScheduleBoardDTO.Resource;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeEvent;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenterShift;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.DowntimeEventRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.WorkCenterRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleBoardServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Monday 5 Oct 2026, 10:00. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Mock
    private WorkCenterRepository workCenterRepository;
    @Mock
    private WorkOrderOperationRepository workOrderOperationRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private DowntimeEventRepository downtimeEventRepository;

    @InjectMocks
    private ScheduleBoardService service;

    private final List<WorkCenter> workCenters = new ArrayList<>();
    private final List<WorkOrderOperation> operations = new ArrayList<>();
    private long nextOperationId = 1;

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(NOW.atZone(IST).toInstant(), IST));
        when(workCenterRepository.findAllActive()).thenReturn(workCenters);
        when(workOrderOperationRepository.findBoardOperations(any(), any(), any(), anyList())).thenReturn(operations);
        when(workOrderOperationRepository.findBookedLoad(any(), any(), anyList(), anyList())).thenReturn(List.of());
        when(workOrderRepository.findOpenWithDueDate(anyList())).thenReturn(List.of());
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED)).thenReturn(List.of());
        when(downtimeEventRepository.findByMachineIdAndEndTimeAfter(any(), any())).thenReturn(List.of());
    }

    @Test
    void anIdleMachineIsListedWithItsCapacityAndNoLoad() {
        WorkCenter cnc = workCenter(1, "WC-CNC");
        MachineDetails busy = machine(1L, "CNC-01", cnc);
        machine(2L, "CNC-02", cnc);
        operations.add(operation(order(10, "WO-10"), cnc, busy, at(5, 9, 0), at(5, 13, 0)));

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY);

        assertThat(board.getResources()).extracting(Resource::getMachineCode).containsExactly("CNC-01", "CNC-02");
        Resource first = board.getResources().get(0);
        Resource idle = board.getResources().get(1);
        assertThat(first.getCapacityMinutes()).isEqualTo(480);
        assertThat(first.getBookedMinutes()).isEqualTo(240);
        assertThat(first.getLoadPercent()).isEqualTo(50.0);
        assertThat(idle.getBars()).isEmpty();
        assertThat(idle.getLoadPercent()).isZero();
        assertThat(board.getKpis().getIdleResources()).isEqualTo(1);
        assertThat(board.getKpis().getLoadPercent()).isEqualTo(25.0);
    }

    @Test
    void openTimeFollowsTheCalendarSoSundayIsClosed() {
        workCenter(1, "WC-CUT");

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY.plusDays(6));

        // Monday to Saturday 09:00-17:00, nothing on Sunday 11 Oct.
        List<ScheduleBoardDTO.Span> open = board.getResources().get(0).getOpenTime();
        assertThat(open).hasSize(6);
        assertThat(open.get(0).getStart()).isEqualTo(at(5, 9, 0));
        assertThat(open.get(5).getEnd()).isEqualTo(at(10, 17, 0));
        assertThat(board.getResources().get(0).getCapacityMinutes()).isEqualTo(6 * 480);
    }

    @Test
    void workRunningIntoTheWindowCountsOnlyThePartInsideIt() {
        WorkCenter cut = workCenter(1, "WC-CUT");
        // Saturday 15:00 to Monday 11:00: two working hours on Saturday, two on Monday.
        operations.add(operation(order(10, "WO-10"), cut, null, at(3, 15, 0), at(5, 11, 0)));

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY);

        Resource row = board.getResources().get(0);
        assertThat(row.getBars()).hasSize(1);
        assertThat(row.getBookedMinutes()).isEqualTo(120);
    }

    @Test
    void anOperationWithNoMachineOnAWorkCentreWithMachinesGetsItsOwnRow() {
        WorkCenter cnc = workCenter(1, "WC-CNC");
        machine(1L, "CNC-01", cnc);
        operations.add(operation(order(10, "WO-10"), cnc, null, at(5, 9, 0), at(5, 10, 0)));

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY);

        assertThat(board.getResources()).hasSize(2);
        Resource unassigned = board.getResources().get(1);
        assertThat(unassigned.isUnassigned()).isTrue();
        assertThat(unassigned.getBars()).hasSize(1);
        // Its time is already counted against the machines, so it adds no capacity.
        assertThat(unassigned.getCapacityMinutes()).isZero();
        assertThat(board.getKpis().getResources()).isEqualTo(1);
    }

    @Test
    void lateAndAtRiskOrdersAreReportedAndTheirBarsMarked() {
        WorkCenter cut = workCenter(1, "WC-CUT");
        WorkOrder late = order(10, "WO-10");
        late.setDueDate(date(at(5, 0, 0)));           // due by the end of Monday
        late.setPlannedEndDate(date(at(6, 12, 0)));   // ends Tuesday noon
        late.setScheduledAt(new Date());
        WorkOrder tight = order(11, "WO-11");
        tight.setDueDate(date(at(6, 0, 0)));
        tight.setPlannedEndDate(date(at(6, 16, 0)));  // eight hours to spare
        tight.setScheduledAt(new Date());
        WorkOrder typedIn = order(12, "WO-12");
        typedIn.setDueDate(date(at(5, 0, 0)));
        typedIn.setPlannedEndDate(date(at(9, 0, 0))); // never scheduled: not a plan
        when(workOrderRepository.findOpenWithDueDate(anyList())).thenReturn(List.of(late, tight, typedIn));
        operations.add(operation(late, cut, null, at(5, 9, 0), at(5, 10, 0)));

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY);

        assertThat(board.getLateOrders()).extracting(ScheduleBoardDTO.OrderAlert::getWorkOrderNumber).containsExactly("WO-10");
        assertThat(board.getLateOrders().get(0).getMinutesLate()).isEqualTo(12 * 60);
        assertThat(board.getAtRiskOrders()).extracting(ScheduleBoardDTO.OrderAlert::getWorkOrderNumber).containsExactly("WO-11");
        assertThat(board.getResources().get(0).getBars().get(0).isLate()).isTrue();
        assertThat(board.getKpis().getLateOrders()).isEqualTo(1);
    }

    @Test
    void newOrdersNotYetScheduledAreListed() {
        WorkOrder fresh = order(10, "WO-10");
        WorkOrder rescheduledBack = order(11, "WO-11");
        rescheduledBack.setScheduledAt(new Date());
        when(workOrderRepository.findByWorkOrderStatus(WorkOrderStatus.CREATED)).thenReturn(List.of(fresh, rescheduledBack));

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY);

        assertThat(board.getUnscheduledOrders()).extracting(ScheduleBoardDTO.OrderAlert::getWorkOrderNumber).containsExactly("WO-10");
    }

    @Test
    void aMachineDownNowOrNotActiveIsReported() {
        WorkCenter cnc = workCenter(1, "WC-CNC");
        MachineDetails down = machine(1L, "CNC-01", cnc);
        MachineDetails maintenance = machine(2L, "CNC-02", cnc);
        maintenance.setMachineStatus(MachineDetails.MachineStatus.UNDER_MAINTENANCE);
        machine(3L, "CNC-03", cnc);

        DowntimeEvent breakdown = new DowntimeEvent();
        breakdown.setMachine(down);
        breakdown.setStartTime(date(at(5, 9, 30)));
        breakdown.setEndTime(date(at(5, 12, 0)));
        breakdown.setRemarks("Spindle bearing");
        when(downtimeEventRepository.findByMachineIdAndEndTimeAfter(eq(1L), any())).thenReturn(List.of(breakdown));

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY);

        assertThat(board.getMachinesDown()).extracting(ScheduleBoardDTO.MachineAlert::getMachineCode)
                .containsExactly("CNC-01", "CNC-02");
        assertThat(board.getMachinesDown().get(0).getReason()).isEqualTo("Spindle bearing");
        assertThat(board.getMachinesDown().get(0).getUntil()).isEqualTo(at(5, 12, 0));
        assertThat(board.getResources().get(0).getDowntime()).hasSize(1);
    }

    @Test
    void runningCompletedAndHeldOperationsAreMarkedKept() {
        WorkCenter cut = workCenter(1, "WC-CUT");
        WorkOrder wo = order(10, "WO-10");
        WorkOrderOperation running = operation(wo, cut, null, at(5, 9, 0), at(5, 10, 0));
        running.setStatus(OperationStatus.IN_PROGRESS);
        WorkOrderOperation planned = operation(wo, cut, null, at(5, 10, 0), at(5, 11, 0));
        operations.add(running);
        operations.add(planned);

        ScheduleBoardDTO board = service.board(MONDAY, MONDAY);

        assertThat(board.getResources().get(0).getBars()).extracting(ScheduleBoardDTO.Bar::isKept).containsExactly(true, false);
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    /** Open 09:00–17:00 with no breaks, Monday to Saturday. */
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
        wc.setWorkStations(new ArrayList<>());
        workCenters.add(wc);
        return wc;
    }

    private MachineDetails machine(Long id, String code, WorkCenter wc) {
        MachineDetails machine = new MachineDetails();
        machine.setId(id);
        machine.setMachineCode(code);
        machine.setWorkCenter(wc);
        machine.setMachineStatus(MachineDetails.MachineStatus.ACTIVE);
        wc.getWorkStations().add(machine);
        return machine;
    }

    private WorkOrder order(int id, String number) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setWorkOrderNumber(number);
        wo.setWorkOrderStatus(WorkOrderStatus.SCHEDULED);
        wo.setPlannedQuantity(BigDecimal.ONE);
        return wo;
    }

    private WorkOrderOperation operation(WorkOrder wo, WorkCenter wc, MachineDetails machine,
                                         LocalDateTime start, LocalDateTime end) {
        RoutingOperation routing = new RoutingOperation();
        routing.setSetupTime(BigDecimal.ZERO);
        routing.setRunTime(BigDecimal.valueOf(60));

        WorkOrderOperation op = new WorkOrderOperation();
        op.setId(nextOperationId++);
        op.setSequence(10);
        op.setOperationName("Op");
        op.setWorkOrder(wo);
        op.setWorkCenter(wc);
        op.setAssignedMachine(machine);
        op.setRoutingOperation(routing);
        op.setPlannedQuantity(BigDecimal.ONE);
        op.setStatus(OperationStatus.PLANNED);
        op.setPlannedStartDate(date(start));
        op.setPlannedEndDate(date(end));
        return op;
    }

    private static LocalDateTime at(int dayOfOctober, int hour, int minute) {
        return LocalDateTime.of(2026, 10, dayOfOctober, hour, minute);
    }

    private static Date date(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(IST).toInstant());
    }
}
