package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineStatusHistoryResponseDTO;
import org.springframework.data.domain.Page;

public interface MachineStatusHistoryService {
    Page<MachineStatusHistoryResponseDTO> getByMachineId(Long machineId, int page, int size, String sortDir);
}
