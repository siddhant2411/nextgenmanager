package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogResponseDTO;
import org.springframework.data.domain.Page;

public interface MachineProductionLogService {
    MachineProductionLogResponseDTO createOrUpdate(MachineProductionLogRequestDTO request);
    Page<MachineProductionLogResponseDTO> getByMachineId(Long machineId, int page, int size, String sortDir);
}
