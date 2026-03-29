package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.dto.MachineStatusChangeRequestDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.service.MachineDetailsService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machine-details")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class MachineDetailsController {

    @Autowired
    private MachineDetailsService machineDetailsService;

    Logger logger = LoggerFactory.getLogger(MachineDetailsController.class);

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Fetched successfully")
    @ApiResponse(responseCode = "404", description = "Machine not found")
    public ResponseEntity<?> getMachineById(@PathVariable String id){
        try {
            MachineDetailsResponseDTO machineDetails = machineDetailsService.getMachineDetailsById(Long.parseLong(id));
            return ResponseEntity.ok(machineDetails);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            logger.error("Error in getting the Machine Details: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Fetched successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<List<MachineDetailsResponseDTO>> getAllMachines(){
        try {
            logger.debug("Fetching all machined details");
            List<MachineDetailsResponseDTO> machineDetailsList = machineDetailsService.getMachineList();
            return ResponseEntity.ok(machineDetailsList);
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @PostMapping("/filter")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Fetched successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public Page<MachineDetailsResponseDTO> filterMachines(@RequestBody FilterRequest request) {
        return machineDetailsService.filterMachineDetails(request);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Updated successfully")
    @ApiResponse(responseCode = "404", description = "Resource Not Found")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<?> updateMachineDetails(@Valid @PathVariable String id, @RequestBody MachineDetails machineDetails){
        try {
            MachineDetailsResponseDTO updatedMachineDetails = machineDetailsService.updateMachineDetails(Long.parseLong(id),machineDetails);
            return ResponseEntity.ok(updatedMachineDetails);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PatchMapping("/{id}/status")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Updated successfully")
    @ApiResponse(responseCode = "404", description = "Resource Not Found")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<?> changeMachineStatus(@PathVariable Long id, @Valid @RequestBody MachineStatusChangeRequestDTO request) {
        try {
            MachineDetailsResponseDTO updatedMachineDetails = request.getStartDate() != null
                    ? machineDetailsService.changeMachineStatus(
                    id,
                    request.getNewStatus(),
                    request.getReason(),
                    request.getStartDate().atStartOfDay()
            )
                    : machineDetailsService.changeMachineStatus(id, request.getNewStatus(), request.getReason());
            return ResponseEntity.ok(updatedMachineDetails);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<?> createMachine(@Valid @RequestBody MachineDetails newMachineDetails){
        try {
            MachineDetailsResponseDTO machineDetails = machineDetailsService.createMachineDetails(newMachineDetails);
            return ResponseEntity.status(201).body(machineDetails);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Updated successfully")
    @ApiResponse(responseCode = "404", description = "Resource Not Found")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<?> deleteMachine(@PathVariable String id){
        try {
            machineDetailsService.deleteMachineDetails(Long.parseLong(id));
            return ResponseEntity.ok("Machine Deleted successfully ");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

}

