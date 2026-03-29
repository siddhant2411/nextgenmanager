package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineProductionLog;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineProductionLogRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MachineProductionLogServiceImpl implements MachineProductionLogService {

    private final MachineProductionLogRepository machineProductionLogRepository;
    private final MachineDetailsRepository machineDetailsRepository;

    public MachineProductionLogServiceImpl(MachineProductionLogRepository machineProductionLogRepository,
                                           MachineDetailsRepository machineDetailsRepository) {
        this.machineProductionLogRepository = machineProductionLogRepository;
        this.machineDetailsRepository = machineDetailsRepository;
    }

    @Override
    @Transactional
    public MachineProductionLogResponseDTO createOrUpdate(MachineProductionLogRequestDTO request) {
        validateRequest(request);

        MachineDetails machine = machineDetailsRepository.findByIdAndDeletedDateIsNull(request.getMachineId())
                .orElseThrow(() -> new ResourceNotFoundException("MachineDetails not found for ID: " + request.getMachineId()));

        MachineProductionLog log = findExistingLog(request.getMachineId(), request.getProductionDate(), request.getShiftId())
                .orElseGet(MachineProductionLog::new);

        log.setMachine(machine);
        log.setProductionDate(request.getProductionDate());
        log.setShiftId(request.getShiftId());
        log.setPlannedQuantity(request.getPlannedQuantity());
        log.setActualQuantity(request.getActualQuantity());
        log.setRejectedQuantity(request.getRejectedQuantity());
        log.setRuntimeMinutes(request.getRuntimeMinutes());
        log.setDowntimeMinutes(request.getDowntimeMinutes());

        return toResponse(machineProductionLogRepository.save(log));
    }

    @Override
    public Page<MachineProductionLogResponseDTO> getByMachineId(Long machineId, int page, int size, String sortDir) {
        machineDetailsRepository.findByIdAndDeletedDateIsNull(machineId)
                .orElseThrow(() -> new ResourceNotFoundException("MachineDetails not found for ID: " + machineId));

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by("productionDate").ascending()
                : Sort.by("productionDate").descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return machineProductionLogRepository.findByMachineId(machineId, pageable).map(this::toResponse);
    }

    private java.util.Optional<MachineProductionLog> findExistingLog(Long machineId, java.time.LocalDate productionDate, Long shiftId) {
        if (shiftId == null) {
            return machineProductionLogRepository.findByMachineIdAndProductionDateAndShiftIdIsNull(machineId, productionDate);
        }
        return machineProductionLogRepository.findByMachineIdAndProductionDateAndShiftId(machineId, productionDate, shiftId);
    }

    private void validateRequest(MachineProductionLogRequestDTO request) {
        if (request.getMachineId() == null) {
            throw new IllegalArgumentException("Machine ID is required");
        }
        if (request.getProductionDate() == null) {
            throw new IllegalArgumentException("Production date is required");
        }
        validateNonNegative("plannedQuantity", request.getPlannedQuantity());
        validateNonNegative("actualQuantity", request.getActualQuantity());
        validateNonNegative("rejectedQuantity", request.getRejectedQuantity());
        validateNonNegative("runtimeMinutes", request.getRuntimeMinutes());
        validateNonNegative("downtimeMinutes", request.getDowntimeMinutes());
    }

    private void validateNonNegative(String field, Integer value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be zero or positive");
        }
    }

    private MachineProductionLogResponseDTO toResponse(MachineProductionLog log) {
        return MachineProductionLogResponseDTO.builder()
                .id(log.getId())
                .machineId(log.getMachine().getId())
                .productionDate(log.getProductionDate())
                .shiftId(log.getShiftId())
                .plannedQuantity(log.getPlannedQuantity())
                .actualQuantity(log.getActualQuantity())
                .rejectedQuantity(log.getRejectedQuantity())
                .runtimeMinutes(log.getRuntimeMinutes())
                .downtimeMinutes(log.getDowntimeMinutes())
                .build();
    }
}
