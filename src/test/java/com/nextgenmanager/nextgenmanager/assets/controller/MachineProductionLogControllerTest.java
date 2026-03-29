package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineProductionLogResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.service.MachineProductionLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineProductionLogControllerTest {

    @Mock
    private MachineProductionLogService machineProductionLogService;

    @InjectMocks
    private MachineProductionLogController controller;

    @Test
    void createOrUpdate_returns201() {
        MachineProductionLogRequestDTO request = new MachineProductionLogRequestDTO();
        request.setMachineId(1L);
        request.setProductionDate(LocalDate.of(2026, 2, 25));
        MachineProductionLogResponseDTO responseDTO = MachineProductionLogResponseDTO.builder().id(1L).machineId(1L).build();
        when(machineProductionLogService.createOrUpdate(request)).thenReturn(responseDTO);

        ResponseEntity<MachineProductionLogResponseDTO> response = controller.createOrUpdate(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(responseDTO);
    }

    @Test
    void getByMachineId_returnsPaged200() {
        Page<MachineProductionLogResponseDTO> page = new PageImpl<>(List.of(
                MachineProductionLogResponseDTO.builder().id(1L).machineId(1L).build()
        ));
        when(machineProductionLogService.getByMachineId(1L, 0, 20, "desc")).thenReturn(page);

        ResponseEntity<Page<MachineProductionLogResponseDTO>> response = controller.getByMachineId(1L, 0, 20, "desc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
    }
}

