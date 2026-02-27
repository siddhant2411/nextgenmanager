package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.service.MachineProductionLogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class MachineProductionLogController {

    private final MachineProductionLogService machineProductionLogService;

    public MachineProductionLogController(MachineProductionLogService machineProductionLogService) {
        this.machineProductionLogService = machineProductionLogService;
    }

    @PostMapping("/api/machine-production-logs")
    public ResponseEntity<MachineProductionLogResponseDTO> createOrUpdate(@Valid @RequestBody MachineProductionLogRequestDTO request) {
        MachineProductionLogResponseDTO response = machineProductionLogService.createOrUpdate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/machines/{id}/production-logs")
    public ResponseEntity<Page<MachineProductionLogResponseDTO>> getByMachineId(@PathVariable Long id,
                                                                                 @RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "20") int size,
                                                                                 @RequestParam(defaultValue = "desc") String sortDir) {
        Page<MachineProductionLogResponseDTO> response = machineProductionLogService.getByMachineId(id, page, size, sortDir);
        return ResponseEntity.ok(response);
    }
}
