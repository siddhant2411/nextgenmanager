package com.nextgenmanager.nextgenmanager.production.controller;


import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProduction;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderProductionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/workOrder")
public class WorkOrderProductionController {

    @Autowired
    private WorkOrderProductionService workOrderProductionService;
    Logger logger = LoggerFactory.getLogger(WorkOrderProductionController.class);

    @PostMapping
    public ResponseEntity<WorkOrderProductionDTO> createWorkOrder(@RequestBody WorkOrderProduction workOrderProduction) {
        try {
            WorkOrderProductionDTO newWorkOrderProduction = workOrderProductionService.createWorkOrderProduction(workOrderProduction);
            return ResponseEntity.status(HttpStatus.CREATED).body(newWorkOrderProduction);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

}
