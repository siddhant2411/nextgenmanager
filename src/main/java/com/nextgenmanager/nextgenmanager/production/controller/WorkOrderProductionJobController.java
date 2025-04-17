package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderProductionTemplateService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/workOrderProductionTemplate")
public class WorkOrderProductionJobController {

    @Autowired
    private WorkOrderProductionTemplateService workOrderProductionTemplateService;

    Logger logger = LoggerFactory.getLogger(WorkOrderProductionJobController.class);

    @GetMapping("/{id}")
    public ResponseEntity<?> getTemplateById(@PathVariable String id) {
        try {
            WorkOrderProductionTemplate template = workOrderProductionTemplateService.getWorkOrderProductionTemplate(Integer.parseInt(id));
            return ResponseEntity.ok(template);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<WorkOrderProductionTemplate>> getAllTemplates() {
        try {
            logger.debug("Fetching all work order production templates");
            List<WorkOrderProductionTemplate> templates = workOrderProductionTemplateService.getWorkOrderProductionTemplateList();
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PostMapping
    public ResponseEntity<WorkOrderProductionTemplate> createTemplate(@RequestBody WorkOrderProductionTemplate template) {
        try {
            WorkOrderProductionTemplate newTemplate = workOrderProductionTemplateService.createWorkOrderProductionTemplate(template);
            return ResponseEntity.status(HttpStatus.CREATED).body(newTemplate);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkOrderProductionTemplate> updateTemplate(@PathVariable String id, @RequestBody WorkOrderProductionTemplate template) {
        try {
            WorkOrderProductionTemplate updatedTemplate = workOrderProductionTemplateService.updateWorkOrderProductionTemplate(Integer.parseInt(id), template);
            return ResponseEntity.ok(updatedTemplate);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable String id) {
        try {
            workOrderProductionTemplateService.deleteWorkOrderProductionTemplate(Integer.parseInt(id));
            return ResponseEntity.ok("Work Order Production Template deleted successfully");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }
}
