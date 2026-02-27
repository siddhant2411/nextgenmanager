package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineProductionLog;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineProductionLogRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineProductionLogServiceImplTest {

    @Mock
    private MachineProductionLogRepository machineProductionLogRepository;
    @Mock
    private MachineDetailsRepository machineDetailsRepository;

    @InjectMocks
    private MachineProductionLogServiceImpl service;

    @Test
    void createOrUpdate_createsWhenNoExistingLog() {
        MachineProductionLogRequestDTO request = request(1L, LocalDate.of(2026, 2, 25), 1L);
        MachineDetails machine = machine(1L);

        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(machine));
        when(machineProductionLogRepository.findByMachineIdAndProductionDateAndShiftId(1L, request.getProductionDate(), 1L))
                .thenReturn(Optional.empty());
        when(machineProductionLogRepository.save(any(MachineProductionLog.class)))
                .thenAnswer(invocation -> {
                    MachineProductionLog log = invocation.getArgument(0);
                    log.setId(101L);
                    return log;
                });

        MachineProductionLogResponseDTO response = service.createOrUpdate(request);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getMachineId()).isEqualTo(1L);
        assertThat(response.getPlannedQuantity()).isEqualTo(100);
    }

    @Test
    void createOrUpdate_updatesWhenExistingLogFound() {
        MachineProductionLogRequestDTO request = request(1L, LocalDate.of(2026, 2, 25), 1L);
        MachineDetails machine = machine(1L);
        MachineProductionLog existing = new MachineProductionLog();
        existing.setId(202L);
        existing.setMachine(machine);
        existing.setProductionDate(request.getProductionDate());
        existing.setShiftId(1L);

        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(machine));
        when(machineProductionLogRepository.findByMachineIdAndProductionDateAndShiftId(1L, request.getProductionDate(), 1L))
                .thenReturn(Optional.of(existing));
        when(machineProductionLogRepository.save(any(MachineProductionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MachineProductionLogResponseDTO response = service.createOrUpdate(request);

        assertThat(response.getId()).isEqualTo(202L);
        assertThat(response.getActualQuantity()).isEqualTo(95);
    }

    @Test
    void createOrUpdate_usesNullShiftFinderWhenShiftIsNull() {
        MachineProductionLogRequestDTO request = request(1L, LocalDate.of(2026, 2, 25), null);
        MachineDetails machine = machine(1L);

        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(machine));
        when(machineProductionLogRepository.findByMachineIdAndProductionDateAndShiftIdIsNull(1L, request.getProductionDate()))
                .thenReturn(Optional.empty());
        when(machineProductionLogRepository.save(any(MachineProductionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createOrUpdate(request);

        verify(machineProductionLogRepository).findByMachineIdAndProductionDateAndShiftIdIsNull(1L, request.getProductionDate());
    }

    @Test
    void createOrUpdate_throwsWhenMachineMissing() {
        MachineProductionLogRequestDTO request = request(9L, LocalDate.of(2026, 2, 25), 1L);
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrUpdate(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MachineDetails not found");
    }

    @Test
    void createOrUpdate_throwsWhenNegativeValuesProvided() {
        MachineProductionLogRequestDTO request = request(1L, LocalDate.of(2026, 2, 25), 1L);
        request.setRuntimeMinutes(-1);

        assertThatThrownBy(() -> service.createOrUpdate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtimeMinutes must be zero or positive");
    }

    @Test
    void getByMachineId_usesDescSortByDefaultAndReturnsPage() {
        MachineDetails machine = machine(1L);
        MachineProductionLog log = new MachineProductionLog();
        log.setId(1L);
        log.setMachine(machine);
        log.setProductionDate(LocalDate.of(2026, 2, 25));
        log.setPlannedQuantity(100);

        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(machine));
        when(machineProductionLogRepository.findByMachineId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<MachineProductionLogResponseDTO> page = service.getByMachineId(1L, 0, 20, "desc");
        assertThat(page.getTotalElements()).isEqualTo(1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(machineProductionLogRepository).findByMachineId(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().toString()).contains("productionDate: DESC");
    }

    @Test
    void getByMachineId_usesAscSortWhenRequested() {
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(machine(1L)));
        when(machineProductionLogRepository.findByMachineId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getByMachineId(1L, 0, 20, "asc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(machineProductionLogRepository).findByMachineId(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().toString()).contains("productionDate: ASC");
    }

    private static MachineProductionLogRequestDTO request(Long machineId, LocalDate productionDate, Long shiftId) {
        MachineProductionLogRequestDTO request = new MachineProductionLogRequestDTO();
        request.setMachineId(machineId);
        request.setProductionDate(productionDate);
        request.setShiftId(shiftId);
        request.setPlannedQuantity(100);
        request.setActualQuantity(95);
        request.setRejectedQuantity(5);
        request.setRuntimeMinutes(400);
        request.setDowntimeMinutes(80);
        return request;
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

