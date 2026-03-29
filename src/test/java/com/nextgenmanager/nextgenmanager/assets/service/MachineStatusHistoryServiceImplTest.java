package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineStatusHistoryResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineStatusHistory;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineStatusHistoryRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineStatusHistoryServiceImplTest {

    @Mock
    private MachineStatusHistoryRepository machineStatusHistoryRepository;
    @Mock
    private MachineDetailsRepository machineDetailsRepository;

    @InjectMocks
    private MachineStatusHistoryServiceImpl service;

    @Test
    void getByMachineId_returnsMappedPage() {
        MachineDetails machine = machine(1L);
        MachineStatusHistory history = new MachineStatusHistory();
        history.setId(8L);
        history.setMachine(machine);
        history.setOldStatus(MachineDetails.MachineStatus.ACTIVE);
        history.setNewStatus(MachineDetails.MachineStatus.BREAKDOWN);
        history.setChangedAt(LocalDateTime.of(2026, 2, 25, 10, 0));
        history.setChangedBy("SYSTEM");
        history.setReason("Auto update");
        history.setSource(MachineStatusHistory.Source.SYSTEM);
        history.setCreatedAt(LocalDateTime.of(2026, 2, 25, 10, 1));

        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(machine));
        when(machineStatusHistoryRepository.findByMachineId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(history)));

        Page<MachineStatusHistoryResponseDTO> page = service.getByMachineId(1L, 0, 20, "desc");

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getNewStatus()).isEqualTo(MachineDetails.MachineStatus.BREAKDOWN);
        assertThat(page.getContent().get(0).getReason()).isEqualTo("Auto update");
    }

    @Test
    void getByMachineId_usesAscSortWhenRequested() {
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(machine(1L)));
        when(machineStatusHistoryRepository.findByMachineId(any(), any(Pageable.class))).thenReturn(Page.empty());

        service.getByMachineId(1L, 0, 20, "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(machineStatusHistoryRepository).findByMachineId(any(), captor.capture());
        assertThat(captor.getValue().getSort().toString()).contains("changedAt: ASC");
    }

    @Test
    void getByMachineId_throwsWhenMachineMissing() {
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByMachineId(1L, 0, 20, "desc"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MachineDetails not found");
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

