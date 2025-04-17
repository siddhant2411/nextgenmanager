package com.nextgenmanager.nextgenmanager.production.controller;


import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import com.nextgenmanager.nextgenmanager.production.service.ProductionJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/production_job")
public class ProductionJobController {

    @Autowired
    private ProductionJobService productionJobService;

    Logger logger = LoggerFactory.getLogger(ProductionJobController.class);
    @GetMapping("/{id}")
    public ResponseEntity<?> getJobById(@PathVariable String id){
        try {
            ProductionJob productionJob = productionJobService.getProductionJobById(Integer.parseInt(id));
            return ResponseEntity.ok(productionJob);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<ProductionJob>> getAllJobs(){
        try {
            logger.debug("Fetching all production jobs");
            List<ProductionJob> productionJobs = productionJobService.getProductionJobList();
            return ResponseEntity.ok(productionJobs);
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductionJob> updateJob(@RequestParam String id, @RequestBody ProductionJob productionJob){
        try {
            ProductionJob updateProductionJob = productionJobService.updateProductionJob(Integer.parseInt(id),productionJob);
            return ResponseEntity.ok(updateProductionJob);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @PostMapping
    public ResponseEntity<ProductionJob> createJob(@RequestBody ProductionJob productionJob){
        try {
            ProductionJob newProductionJob = productionJobService.createProductionJob(productionJob);
            return ResponseEntity.status(201).body(newProductionJob);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

}
