package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineStatusChangeRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.service.MachineDetailsService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineDetailsControllerTest {

    @Mock
    private MachineDetailsService machineDetailsService;

    @InjectMocks
    private MachineDetailsController controller;

    @Test
    void getMachineById_returns200WhenFound() {
        MachineDetailsResponseDTO dto = MachineDetailsResponseDTO.builder().id(1L).machineCode("M-01").build();
        when(machineDetailsService.getMachineDetailsById(1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getMachineById("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    void getMachineById_returns404WhenMissing() {
        when(machineDetailsService.getMachineDetailsById(1L))
                .thenThrow(new ResourceNotFoundException("MachineDetails not found for ID: 1"));

        ResponseEntity<?> response = controller.getMachineById("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("MachineDetails not found for ID: 1");
    }

    @Test
    void getAllMachines_returns200() {
        when(machineDetailsService.getMachineList()).thenReturn(List.of(MachineDetailsResponseDTO.builder().id(1L).build()));

        ResponseEntity<List<MachineDetailsResponseDTO>> response = controller.getAllMachines();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void createMachine_returns201() {
        MachineDetailsResponseDTO dto = MachineDetailsResponseDTO.builder().id(1L).build();
        when(machineDetailsService.createMachineDetails(any(MachineDetails.class))).thenReturn(dto);

        ResponseEntity<?> response = controller.createMachine(new MachineDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    void createMachine_returns400OnRuntimeError() {
        when(machineDetailsService.createMachineDetails(any(MachineDetails.class))).thenThrow(new IllegalArgumentException("bad"));

        ResponseEntity<?> response = controller.createMachine(new MachineDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateMachineDetails_returns404WhenMissing() {
        when(machineDetailsService.updateMachineDetails(any(Long.class), any(MachineDetails.class)))
                .thenThrow(new ResourceNotFoundException("MachineDetails not found for ID: 1"));

        ResponseEntity<?> response = controller.updateMachineDetails("1", new MachineDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void changeMachineStatus_returns200() {
        MachineStatusChangeRequestDTO request = new MachineStatusChangeRequestDTO();
        request.setNewStatus(MachineDetails.MachineStatus.BREAKDOWN);
        request.setReason("Test");

        when(machineDetailsService.changeMachineStatus(1L, MachineDetails.MachineStatus.BREAKDOWN, "Test"))
                .thenReturn(MachineDetailsResponseDTO.builder().id(1L).machineStatus(MachineDetails.MachineStatus.BREAKDOWN).build());

        ResponseEntity<?> response = controller.changeMachineStatus(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteMachine_returns200() {
        ResponseEntity<?> response = controller.deleteMachine("1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteMachine_returns404WhenMissing() {
        doThrow(new ResourceNotFoundException("MachineDetails not found for ID: 1"))
                .when(machineDetailsService).deleteMachineDetails(1L);

        ResponseEntity<?> response = controller.deleteMachine("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

