package com.nextgenmanager.nextgenmanager.production.controller;


import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.ProductionJobResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import com.nextgenmanager.nextgenmanager.production.service.ProductionJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/production-job")
public class ProductionJobController {

    @Autowired
    private ProductionJobService productionJobService;

    Logger logger = LoggerFactory.getLogger(ProductionJobController.class);
    @GetMapping("/{id}")
    public ResponseEntity<?> getJobById(@PathVariable String id){
        try {
            ProductionJobResponseDTO productionJob = productionJobService.getProductionJobById(Integer.parseInt(id));
            return ResponseEntity.ok(productionJob);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<Page<ProductionJobResponseDTO>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "jobName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String search
    ) {
        try {
            logger.debug("Fetching paginated production jobs");
            Page<ProductionJobResponseDTO> productionJobs = productionJobService.getProductionJobList(page, size, sortBy, sortDir, search);
            return ResponseEntity.ok(productionJobs);
        } catch (Exception e) {
            logger.error("Error fetching production jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(@PathVariable String id, @RequestBody ProductionJob productionJob) {
        try {
            ProductionJobResponseDTO updateProductionJob = productionJobService.updateProductionJob(Integer.parseInt(id),productionJob);
            return ResponseEntity.ok(updateProductionJob);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody ProductionJob productionJob){
        try {
            ProductionJobResponseDTO newProductionJob = productionJobService.createProductionJob(productionJob);
            return ResponseEntity.status(201).body(newProductionJob);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable String id){
        try {
            productionJobService.deleteProductionJob(Integer.parseInt(id));
            return ResponseEntity.ok("Production Job Deleted successfully ");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

}
