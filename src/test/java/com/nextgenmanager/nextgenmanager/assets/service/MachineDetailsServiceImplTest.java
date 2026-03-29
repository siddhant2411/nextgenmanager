package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineStatusHistory;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineStatusHistoryRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineDetailsServiceImplTest {

    @Mock
    private MachineDetailsRepository machineDetailsRepository;
    @Mock
    private MachineDetailsResponseMapper machineDetailsResponseMapper;
    @Mock
    private MachineStatusHistoryRepository machineStatusHistoryRepository;

    @InjectMocks
    private MachineDetailsServiceImpl service;

    @Test
    void createMachineDetails_savesAndReturnsDto() {
        MachineDetails machine = machine(1L, "M-01", MachineDetails.MachineStatus.ACTIVE);
        MachineDetailsResponseDTO responseDTO = MachineDetailsResponseDTO.builder().id(1L).machineCode("M-01").build();

        when(machineDetailsRepository.findByMachineCodeAndDeletedDateIsNull("M-01")).thenReturn(Optional.empty());
        when(machineDetailsRepository.save(machine)).thenReturn(machine);
        when(machineDetailsResponseMapper.toDTO(machine)).thenReturn(responseDTO);

        MachineDetailsResponseDTO response = service.createMachineDetails(machine);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMachineCode()).isEqualTo("M-01");
    }

    @Test
    void createMachineDetails_throwsForDuplicateCode() {
        MachineDetails machine = machine(1L, "M-01", MachineDetails.MachineStatus.ACTIVE);
        when(machineDetailsRepository.findByMachineCodeAndDeletedDateIsNull("M-01")).thenReturn(Optional.of(machine));

        assertThatThrownBy(() -> service.createMachineDetails(machine))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateMachineDetails_savesStatusHistoryWhenStatusChanges() {
        MachineDetails existing = machine(1L, "M-01", MachineDetails.MachineStatus.ACTIVE);
        MachineDetails update = machine(1L, "M-01", MachineDetails.MachineStatus.BREAKDOWN);

        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(existing));
        when(machineDetailsRepository.findByMachineCodeAndDeletedDateIsNull("M-01")).thenReturn(Optional.of(existing));
        when(machineDetailsRepository.save(any(MachineDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(machineDetailsResponseMapper.toDTO(any(MachineDetails.class)))
                .thenReturn(MachineDetailsResponseDTO.builder().id(1L).machineStatus(MachineDetails.MachineStatus.BREAKDOWN).build());

        MachineDetailsResponseDTO response = service.updateMachineDetails(1L, update);

        assertThat(response.getMachineStatus()).isEqualTo(MachineDetails.MachineStatus.BREAKDOWN);
        verify(machineStatusHistoryRepository).save(any(MachineStatusHistory.class));
    }

    @Test
    void updateMachineDetails_throwsWhenNotFound() {
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMachineDetails(5L, machine(5L, "M-05", MachineDetails.MachineStatus.ACTIVE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MachineDetails not found");
    }

    @Test
    void changeMachineStatus_savesHistoryWithProvidedReason() {
        MachineDetails existing = machine(1L, "M-01", MachineDetails.MachineStatus.ACTIVE);
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(existing));
        when(machineDetailsRepository.save(any(MachineDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(machineDetailsResponseMapper.toDTO(any(MachineDetails.class)))
                .thenReturn(MachineDetailsResponseDTO.builder().id(1L).machineStatus(MachineDetails.MachineStatus.UNDER_MAINTENANCE).build());

        service.changeMachineStatus(1L, MachineDetails.MachineStatus.UNDER_MAINTENANCE, "Scheduled PM");

        ArgumentCaptor<MachineStatusHistory> captor = ArgumentCaptor.forClass(MachineStatusHistory.class);
        verify(machineStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("Scheduled PM");
        assertThat(captor.getValue().getSource()).isEqualTo(MachineStatusHistory.Source.MANUAL);
        assertThat(captor.getValue().getChangedBy()).isEqualTo("SYSTEM");
    }

    @Test
    void changeMachineStatus_doesNotWriteHistoryWhenStatusUnchanged() {
        MachineDetails existing = machine(1L, "M-01", MachineDetails.MachineStatus.ACTIVE);
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(existing));
        when(machineDetailsResponseMapper.toDTO(existing))
                .thenReturn(MachineDetailsResponseDTO.builder().id(1L).machineStatus(MachineDetails.MachineStatus.ACTIVE).build());

        service.changeMachineStatus(1L, MachineDetails.MachineStatus.ACTIVE, "No-op");

        verify(machineDetailsRepository, never()).save(any(MachineDetails.class));
        verify(machineStatusHistoryRepository, never()).save(any(MachineStatusHistory.class));
    }

    @Test
    void deleteMachineDetails_softDeletesRecord() {
        MachineDetails existing = machine(1L, "M-01", MachineDetails.MachineStatus.ACTIVE);
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(existing));

        service.deleteMachineDetails(1L);

        verify(machineDetailsRepository).save(existing);
        assertThat(existing.getDeletedDate()).isNotNull();
    }

    @Test
    void getMachineDetailsById_throwsWhenDeletedOrMissing() {
        when(machineDetailsRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMachineDetailsById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MachineDetails not found");
    }

    private static MachineDetails machine(Long id, String code, MachineDetails.MachineStatus status) {
        MachineDetails machine = new MachineDetails();
        machine.setId(id);
        machine.setMachineCode(code);
        machine.setMachineName("Machine " + id);
        machine.setDescription("desc");
        WorkCenter wc = new WorkCenter();
        wc.setId(1);
        wc.setCenterCode("WC-1");
        wc.setCenterName("Center 1");
        machine.setWorkCenter(wc);
        machine.setCostPerHour(BigDecimal.TEN);
        machine.setMachineStatus(status);
        return machine;
    }
}

