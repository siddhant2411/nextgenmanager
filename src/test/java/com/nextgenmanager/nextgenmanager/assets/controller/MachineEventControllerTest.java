package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineEventRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import com.nextgenmanager.nextgenmanager.assets.service.MachineEventService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineEventControllerTest {

    @Mock
    private MachineEventService machineEventService;

    @InjectMocks
    private MachineEventController controller;

    @Test
    void createMachineEvent_returns201() {
        MachineEventRequestDTO request = new MachineEventRequestDTO();
        request.setMachineId(1L);
        request.setEventType(MachineEvent.EventType.BREAKDOWN);
        request.setStartTime(LocalDateTime.of(2026, 2, 25, 10, 0));
        request.setSource(MachineEvent.Source.MANUAL);

        MachineEvent event = new MachineEvent();
        event.setId(10L);
        MachineDetails machine = new MachineDetails();
        machine.setId(1L);
        event.setMachine(machine);
        event.setEventType(MachineEvent.EventType.BREAKDOWN);
        event.setStartTime(request.getStartTime());
        event.setSource(MachineEvent.Source.MANUAL);
        event.setCreatedAt(LocalDateTime.of(2026, 2, 25, 10, 1));

        when(machineEventService.createEvent(1L, request.getEventType(), request.getStartTime(), null, request.getSource()))
                .thenReturn(event);

        ResponseEntity<?> response = controller.createMachineEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void createMachineEvent_returns404WhenMachineMissing() {
        MachineEventRequestDTO request = new MachineEventRequestDTO();
        request.setMachineId(99L);
        request.setEventType(MachineEvent.EventType.RUNNING);
        request.setStartTime(LocalDateTime.of(2026, 2, 25, 10, 0));
        request.setSource(MachineEvent.Source.SYSTEM);

        when(machineEventService.createEvent(99L, request.getEventType(), request.getStartTime(), null, request.getSource()))
                .thenThrow(new ResourceNotFoundException("MachineDetails not found for ID: 99"));

        ResponseEntity<?> response = controller.createMachineEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("MachineDetails not found for ID: 99");
    }

    @Test
    void createMachineEvent_returns400ForRuntimeValidationErrors() {
        MachineEventRequestDTO request = new MachineEventRequestDTO();
        request.setMachineId(1L);
        request.setEventType(MachineEvent.EventType.RUNNING);
        request.setStartTime(LocalDateTime.of(2026, 2, 25, 10, 0));
        request.setSource(MachineEvent.Source.SYSTEM);

        when(machineEventService.createEvent(1L, request.getEventType(), request.getStartTime(), null, request.getSource()))
                .thenThrow(new IllegalArgumentException("Start time is required"));

        ResponseEntity<?> response = controller.createMachineEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Start time is required");
    }
}

