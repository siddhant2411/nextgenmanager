package com.nextgenmanager.nextgenmanager.assets.controller;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.service.MachineDetailsService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.marketing.quotation.controller.QuotationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machine-details")
public class MachineDetailsController {

    @Autowired
    private MachineDetailsService machineDetailsService;

    Logger logger = LoggerFactory.getLogger(QuotationController.class);

    @GetMapping("/{id}")
    public ResponseEntity<?> getMachineById(@PathVariable String id){
        try {
            MachineDetailsResponseDTO machineDetails = machineDetailsService.getMachineDetailsById(Integer.parseInt(id));
            return ResponseEntity.ok(machineDetails);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            logger.error("Error in getting the Machine Details: "+e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<MachineDetailsResponseDTO>> getAllMachines(){
        try {
            logger.debug("Fetching all machined details");
            List<MachineDetailsResponseDTO> machineDetailsList = machineDetailsService.getMachineList();
            return ResponseEntity.ok(machineDetailsList);
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMachineDetails(@RequestParam String id, @RequestBody MachineDetails machineDetails){
        try {
            MachineDetailsResponseDTO updatedMachineDetails = machineDetailsService.updateMachineDetails(Integer.parseInt(id),machineDetails);
            return ResponseEntity.ok(updatedMachineDetails);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @PostMapping
    public ResponseEntity<?> createMachine(@RequestBody MachineDetails newMachineDetails){
        try {
            MachineDetailsResponseDTO machineDetails = machineDetailsService.createMachineDetails(newMachineDetails);
            return ResponseEntity.status(201).body(machineDetails);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMachine(@PathVariable String id){
        try {
            machineDetailsService.deleteMachineDetails(Integer.parseInt(id));
            return ResponseEntity.ok("Machine Deleted successfully ");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

}
