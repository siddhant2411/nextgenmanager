package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.service.WorkCenterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/manufacturing/work-center")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class WorkCenterController {

    @Autowired
    private WorkCenterService workCenterService;

    private static final Logger logger = LoggerFactory.getLogger(WorkCenterController.class);
    @PostMapping
    public ResponseEntity<WorkCenterResponseDTO> createWorkCenter(@RequestBody WorkCenter workCenter){
        try {
            WorkCenterResponseDTO newWorkCenter = workCenterService.createWorkCenter(workCenter);
            return ResponseEntity.status(201).body(newWorkCenter);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateWorkCenter(@PathVariable String workCenterId, @RequestBody WorkCenter updatedWorkCenter){
        try {
            WorkCenterResponseDTO newWorkCenter = workCenterService.updateWorkCenter(Integer.parseInt(workCenterId),updatedWorkCenter);
            return ResponseEntity.status(201).body(newWorkCenter);
        } catch (ResourceNotFoundException e){
            logger.error("Work Center with ID {} does not exist", workCenterId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Failed to delete Work Center: " + e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkCenter(@PathVariable Integer id){
        try {
            WorkCenterResponseDTO newWorkCenter = workCenterService.getWorkCenterById(id);
            return ResponseEntity.status(201).body(newWorkCenter);
        } catch (ResourceNotFoundException e){
            logger.error("Work Center with ID {} does not exist", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Failed to fetch Work Center: " + e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWorkCenter(@PathVariable Integer id) {
        logger.debug("Received request to delete Work Center with id: {}", id);
        try {
            workCenterService.deleteWorkCenter(id);

            logger.info("Successfully deleted Work Center ID: {}", id);
            return ResponseEntity.status(HttpStatus.OK).body("Bom with id: "+id+" is deleted");
        }catch (ResourceNotFoundException e){
            logger.error("Work Center with ID {} does not exist", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Failed to delete Work Center: " + e.getMessage()));
        }
        catch (Exception e) {
            logger.error("Failed to delete Work Center ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete Work Center: " + e.getMessage()));
        }
    }


    @GetMapping("/search")
    public ResponseEntity<?> searchWorkCenter(    @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(defaultValue = "centerName") String sortBy,
                                                  @RequestParam(defaultValue = "asc") String sortDir,
                                                  @RequestParam(required = false) String search){
        try {
            Page<WorkCenterResponseDTO> workCenterResponseDTOS = workCenterService.getPaginatedCenters(page,size,sortBy,sortDir,search);
            return ResponseEntity.status(201).body(workCenterResponseDTOS);
        } catch (ResourceNotFoundException e){
            logger.error("Work Center does not exist");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Failed to fetch Work Center: " + e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }
}

