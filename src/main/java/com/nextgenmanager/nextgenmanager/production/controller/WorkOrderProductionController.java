package com.nextgenmanager.nextgenmanager.production.controller;


import com.nextgenmanager.nextgenmanager.marketing.enquiry.controller.WorkOrderListRequest;
import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionDTO;
import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionRequestMapper;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProduction;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderProductionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/production/workOrder")
public class WorkOrderProductionController {

    @Autowired
    private WorkOrderProductionService workOrderProductionService;
    Logger logger = LoggerFactory.getLogger(WorkOrderProductionController.class);

    @PostMapping
    public ResponseEntity<?> createWorkOrder(@RequestBody WorkOrderProductionRequestMapper workOrderProductionRequestMapper) {
        try {
            WorkOrderProduction workOrderProduction = workOrderProductionService.mapWorkOrderProductionRequest(workOrderProductionRequestMapper);
            WorkOrderProductionDTO newWorkOrderProduction = workOrderProductionService.createWorkOrderProduction(workOrderProduction);
            return ResponseEntity.status(HttpStatus.CREATED).body(newWorkOrderProduction);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }
    }




    @PostMapping("/get-list")
    public ResponseEntity<?> getWorkOrderList(@RequestBody WorkOrderListRequest paramObject) {
        try {
            int page = paramObject.getPage();
            int size = paramObject.getSize();
            String sortBy = paramObject.getSortBy();
            String sortDir = paramObject.getSortDir();
            WorkOrderProductionDTO filterDTO = paramObject.getFilterDTO();

            Page<WorkOrderProductionDTO> workOrderProductionList =
                    workOrderProductionService.getWorkOrderProductionList(filterDTO, page, size,sortBy,sortDir);

            return ResponseEntity.status(HttpStatus.OK).body(workOrderProductionList);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error while fetching work order list: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getWorkOrder(@RequestParam String id){
        try{
            Optional<WorkOrderProduction> workOrderProduction = workOrderProductionService.getWorkOrderProductionJobById(Integer.parseInt(id));
            return ResponseEntity.status(HttpStatus.OK).body(workOrderProduction);
        }
        catch (RuntimeException e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error while fetching work order : " + e.getMessage());
        }
    }


}
