package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.production.dto.DowntimeEventRequestDTO;
import com.nextgenmanager.nextgenmanager.production.dto.DowntimeEventResponseDTO;
import com.nextgenmanager.nextgenmanager.production.dto.DowntimeReasonCodeDTO;
import com.nextgenmanager.nextgenmanager.production.service.workcenter.DowntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresProductionModuleAccess;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/downtime")
@RequiredArgsConstructor
@RequiresProductionModuleAccess
public class DowntimeController {

    private final DowntimeService downtimeService;

    @GetMapping("/reasons")
    public List<DowntimeReasonCodeDTO> getAllReasons() {
        return downtimeService.getAllReasonCodes();
    }

    @PostMapping("/reasons")
    public DowntimeReasonCodeDTO createReason(@RequestBody DowntimeReasonCodeDTO dto) {
        return downtimeService.createReasonCode(dto);
    }

    @PostMapping("/start")
    public ResponseEntity<DowntimeEventResponseDTO> startDowntime(@RequestBody DowntimeEventRequestDTO request) {
        return ResponseEntity.ok(downtimeService.startDowntime(request));
    }

    @PostMapping("/stop/{eventId}")
    public ResponseEntity<DowntimeEventResponseDTO> stopDowntime(@PathVariable Long eventId) {
        return ResponseEntity.ok(downtimeService.stopDowntime(eventId));
    }

    @GetMapping("/active/{machineId}")
    public ResponseEntity<DowntimeEventResponseDTO> getActiveEvent(@PathVariable Long machineId) {
        return downtimeService.getActiveEventByMachine(machineId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
