package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineStatusHistoryResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.service.MachineStatusHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineStatusHistoryControllerTest {

    @Mock
    private MachineStatusHistoryService machineStatusHistoryService;

    @InjectMocks
    private MachineStatusHistoryController controller;

    @Test
    void getMachineStatusHistory_returnsPaged200() {
        Page<MachineStatusHistoryResponseDTO> page = new PageImpl<>(
                List.of(MachineStatusHistoryResponseDTO.builder().id(1L).machineId(1L).reason("Reason").build())
        );
        when(machineStatusHistoryService.getByMachineId(1L, 0, 20, "desc")).thenReturn(page);

        ResponseEntity<?> response = controller.getMachineStatusHistory(1L, 0, 20, "desc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(page);
    }
}

