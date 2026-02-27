package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineStatusHistoryResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.service.MachineStatusHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/machines")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class MachineStatusHistoryController {

    private final MachineStatusHistoryService machineStatusHistoryService;

    public MachineStatusHistoryController(MachineStatusHistoryService machineStatusHistoryService) {
        this.machineStatusHistoryService = machineStatusHistoryService;
    }

    @GetMapping("/{id}/status-history")
    public ResponseEntity<?> getMachineStatusHistory(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(defaultValue = "desc") String sortDir) {
        Page<MachineStatusHistoryResponseDTO> history = machineStatusHistoryService.getByMachineId(id, page, size, sortDir);
        return ResponseEntity.ok(history);
    }

}
