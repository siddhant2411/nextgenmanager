package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineStatusHistoryResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineStatusHistory;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineStatusHistoryRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class MachineStatusHistoryServiceImpl implements MachineStatusHistoryService {

    private final MachineStatusHistoryRepository machineStatusHistoryRepository;
    private final MachineDetailsRepository machineDetailsRepository;

    public MachineStatusHistoryServiceImpl(MachineStatusHistoryRepository machineStatusHistoryRepository,
                                           MachineDetailsRepository machineDetailsRepository) {
        this.machineStatusHistoryRepository = machineStatusHistoryRepository;
        this.machineDetailsRepository = machineDetailsRepository;
    }

    @Override
    public Page<MachineStatusHistoryResponseDTO> getByMachineId(Long machineId, int page, int size, String sortDir) {
        machineDetailsRepository.findByIdAndDeletedDateIsNull(machineId)
                .orElseThrow(() -> new ResourceNotFoundException("MachineDetails not found for ID: " + machineId));

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by("changedAt").ascending()
                : Sort.by("changedAt").descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return machineStatusHistoryRepository.findByMachineId(machineId, pageable)
                .map(item -> MachineStatusHistoryResponseDTO.builder()
                        .id(item.getId())
                        .machineId(item.getMachine().getId())
                        .oldStatus(item.getOldStatus())
                        .newStatus(item.getNewStatus())
                        .startedAt(item.getChangedAt())
                        .endedAt(resolveEndedAt(item))
                        .changedBy(item.getChangedBy())
                        .reason(item.getReason())
                        .source(item.getSource())
                        .createdAt(item.getCreatedAt())
                        .build());
    }

    private java.time.LocalDateTime resolveEndedAt(MachineStatusHistory item) {
        return machineStatusHistoryRepository.findNextStatusEntries(
                        item.getMachine().getId(),
                        item.getChangedAt(),
                        item.getId(),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(MachineStatusHistory::getChangedAt)
                .orElse(null);
    }
}
