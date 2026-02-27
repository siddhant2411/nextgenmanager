package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineEventRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineEventServiceImplTest {

    @Mock
    private MachineEventRepository machineEventRepository;
    @Mock
    private MachineDetailsRepository machineDetailsRepository;
    @Mock
    private MachineDetailsService machineDetailsService;

    @InjectMocks
    private MachineEventServiceImpl service;

    @Test
    void createEvent_throwsWhenMachineIdMissing() {
        assertThatThrownBy(() -> service.createEvent(
                null,
                MachineEvent.EventType.RUNNING,
                LocalDateTime.now(),
                null,
                MachineEvent.Source.MANUAL
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Machine ID is required");
    }

    @Test
    void createEvent_throwsWhenMachineMissing() {
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEvent(
                99L,
                MachineEvent.EventType.RUNNING,
                LocalDateTime.now(),
                null,
                MachineEvent.Source.MANUAL
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MachineDetails not found");
    }

    @Test
    void save_throwsWhenEndTimeBeforeStartTime() {
        MachineEvent event = new MachineEvent();
        event.setMachine(machine(1L));
        event.setEventType(MachineEvent.EventType.RUNNING);
        event.setSource(MachineEvent.Source.MANUAL);
        event.setStartTime(LocalDateTime.of(2026, 2, 25, 10, 0));
        event.setEndTime(LocalDateTime.of(2026, 2, 25, 9, 0));

        assertThatThrownBy(() -> service.save(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End time cannot be before start time");
    }

    @Test
    void save_autoClosesOpenEventAndMapsBreakdownStatus() {
        MachineEvent openEvent = new MachineEvent();
        openEvent.setId(11L);
        openEvent.setMachine(machine(1L));
        openEvent.setEventType(MachineEvent.EventType.RUNNING);
        openEvent.setStartTime(LocalDateTime.of(2026, 2, 25, 9, 0));

        MachineEvent newEvent = new MachineEvent();
        newEvent.setMachine(machine(1L));
        newEvent.setEventType(MachineEvent.EventType.BREAKDOWN);
        newEvent.setSource(MachineEvent.Source.MANUAL);
        newEvent.setStartTime(LocalDateTime.of(2026, 2, 25, 10, 0));

        when(machineEventRepository.findFirstByMachineIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(openEvent));
        when(machineEventRepository.save(any(MachineEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MachineEvent saved = service.save(newEvent);

        assertThat(saved.getEventType()).isEqualTo(MachineEvent.EventType.BREAKDOWN);
        assertThat(openEvent.getEndTime()).isEqualTo(LocalDateTime.of(2026, 2, 25, 10, 0));

        verify(machineDetailsService).changeMachineStatus(
                1L,
                MachineDetails.MachineStatus.BREAKDOWN,
                "Auto-updated from machine event: BREAKDOWN"
        );
    }

    @Test
    void save_throwsWhenNewStartBeforeOpenEventStart() {
        MachineEvent openEvent = new MachineEvent();
        openEvent.setId(10L);
        openEvent.setMachine(machine(1L));
        openEvent.setStartTime(LocalDateTime.of(2026, 2, 25, 11, 0));

        MachineEvent newEvent = new MachineEvent();
        newEvent.setMachine(machine(1L));
        newEvent.setEventType(MachineEvent.EventType.IDLE);
        newEvent.setSource(MachineEvent.Source.SYSTEM);
        newEvent.setStartTime(LocalDateTime.of(2026, 2, 25, 10, 30));

        when(machineEventRepository.findFirstByMachineIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(openEvent));

        assertThatThrownBy(() -> service.save(newEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be before current active event start time");

        verify(machineDetailsService, never()).changeMachineStatus(anyLong(), any(), any());
    }

    @Test
    void save_mapsMaintenanceAndRunningToExpectedStatuses() {
        MachineEvent maintenanceEvent = new MachineEvent();
        maintenanceEvent.setMachine(machine(1L));
        maintenanceEvent.setEventType(MachineEvent.EventType.MAINTENANCE);
        maintenanceEvent.setSource(MachineEvent.Source.MANUAL);
        maintenanceEvent.setStartTime(LocalDateTime.of(2026, 2, 25, 12, 0));

        when(machineEventRepository.findFirstByMachineIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.empty());
        when(machineEventRepository.save(any(MachineEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.save(maintenanceEvent);
        verify(machineDetailsService).changeMachineStatus(
                1L,
                MachineDetails.MachineStatus.UNDER_MAINTENANCE,
                "Auto-updated from machine event: MAINTENANCE"
        );

        MachineEvent runningEvent = new MachineEvent();
        runningEvent.setMachine(machine(1L));
        runningEvent.setEventType(MachineEvent.EventType.RUNNING);
        runningEvent.setSource(MachineEvent.Source.SYSTEM);
        runningEvent.setStartTime(LocalDateTime.of(2026, 2, 25, 13, 0));

        service.save(runningEvent);
        verify(machineDetailsService).changeMachineStatus(
                1L,
                MachineDetails.MachineStatus.ACTIVE,
                "Auto-updated from machine event: RUNNING"
        );
    }

    private static MachineDetails machine(Long id) {
        MachineDetails machine = new MachineDetails();
        machine.setId(id);
        machine.setMachineCode("M-" + id);
        machine.setMachineName("Machine " + id);
        machine.setCostPerHour(BigDecimal.TEN);
        machine.setMachineStatus(MachineDetails.MachineStatus.ACTIVE);
        return machine;
    }
}
