package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineEventRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineEventResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import com.nextgenmanager.nextgenmanager.assets.service.MachineEventService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/machine-events")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class MachineEventController {

    private final MachineEventService machineEventService;

    public MachineEventController(MachineEventService machineEventService) {
        this.machineEventService = machineEventService;
    }

    @PostMapping
    public ResponseEntity<?> createMachineEvent(@Valid @RequestBody MachineEventRequestDTO request) {
        try {
            MachineEvent savedEvent = machineEventService.createEvent(
                    request.getMachineId(),
                    request.getEventType(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getSource()
            );
            MachineEventResponseDTO response = MachineEventResponseDTO.builder()
                    .id(savedEvent.getId())
                    .machineId(savedEvent.getMachine().getId())
                    .eventType(savedEvent.getEventType())
                    .startTime(savedEvent.getStartTime())
                    .endTime(savedEvent.getEndTime())
                    .source(savedEvent.getSource())
                    .createdAt(savedEvent.getCreatedAt())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
