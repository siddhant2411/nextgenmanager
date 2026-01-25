package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderProductionTemplateResponseDTO;
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
public class WorkOrderProductionTemplateController {

    @Autowired
    private WorkOrderProductionTemplateService workOrderProductionTemplateService;

    Logger logger = LoggerFactory.getLogger(WorkOrderProductionTemplateController.class);

    @GetMapping("/{id}")
    public ResponseEntity<?> getTemplateById(@PathVariable String id) {
        try {
            WorkOrderProductionTemplateResponseDTO template = workOrderProductionTemplateService.getWorkOrderProductionTemplate(Integer.parseInt(id));
            return ResponseEntity.ok(template);
        } catch (ResourceNotFoundException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<WorkOrderProductionTemplateResponseDTO>> getAllTemplates() {
        try {
            logger.debug("Fetching all work order production templates");
            List<WorkOrderProductionTemplateResponseDTO> templates = workOrderProductionTemplateService.getWorkOrderProductionTemplateList();
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PostMapping
    public ResponseEntity<?> createTemplate(@RequestBody WorkOrderProductionTemplate template) {
        try {
            WorkOrderProductionTemplateResponseDTO newTemplate = workOrderProductionTemplateService.createWorkOrderProductionTemplate(template);
            return ResponseEntity.status(HttpStatus.CREATED).body(newTemplate);
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
        catch (ResourceNotFoundException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }catch (Exception e){
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());

        }

    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTemplate(@PathVariable String id, @RequestBody WorkOrderProductionTemplate template) {
        try {
            WorkOrderProductionTemplateResponseDTO updatedTemplate = workOrderProductionTemplateService.updateWorkOrderProductionTemplate(Integer.parseInt(id), template);
            return ResponseEntity.ok(updatedTemplate);
        }
        catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }catch (ResourceNotFoundException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
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
