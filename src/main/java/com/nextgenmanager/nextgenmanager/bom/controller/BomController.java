package com.nextgenmanager.nextgenmanager.bom.controller;

import com.nextgenmanager.nextgenmanager.bom.dto.BomListDTO;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BOMResponseTemplateMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BOMTemplateMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.bom.service.BomWorkflowService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderProductionTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

@RestController
@RequestMapping("/api/bom")

public class BomController {

    private static final Logger logger = LoggerFactory.getLogger(BomController.class);


    @Autowired
    private BomService bomService;

    @Autowired
    private BomWorkflowService bomWorkflowService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getBom(@PathVariable Integer id) {
        logger.info("Received request to fetch BOM with id: {}", id);
        try {
            Bom bom = bomService.getBom(id);
            if (bom == null) {
                logger.warn("BOM not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "BOM not found with ID: " + id));
            }

            WorkOrderProductionTemplate workOrderProductionTemplate = bomService.getBomWOTemplateByBomId(bom.getId());
            if (workOrderProductionTemplate == null) {
                logger.warn("WorkOrderProductionTemplate not found for BOM ID: {}", id);
            }

            BOMResponseTemplateMapper bomTemplateMapper = new BOMResponseTemplateMapper();
            bomTemplateMapper.setBom(BomMapper.toDto(bom));
            bomTemplateMapper.setWorkOrderProductionTemplate(workOrderProductionTemplate);

            logger.info("Successfully fetched BOM and template for ID: {}", id);
            return ResponseEntity.ok(bomTemplateMapper);

        } catch (ResourceNotFoundException e) {
            logger.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal error while fetching BOM ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error occurred"));
        }
    }


    @PostMapping
    public ResponseEntity<?> addBom(@RequestBody BOMTemplateMapper bomTemplateMapper) {
        logger.debug("Received request to add new BOM");

        try {

            BOMTemplateMapper newBomTemplateMapper = bomWorkflowService.createBomWithTemplate(bomTemplateMapper);
            return ResponseEntity.status(HttpStatus.CREATED).body(newBomTemplateMapper);

        } catch (Exception e) {
            logger.error("Failed to create BOM: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create BOM: " + e.getMessage()));
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateBom(@PathVariable Integer id, @RequestBody BOMTemplateMapper bomTemplateMapper) {
        try{
            BOMTemplateMapper responseMapper = bomWorkflowService.updateBomWithTemplate(id,bomTemplateMapper);
            return ResponseEntity.ok(responseMapper);

        } catch (Exception e) {
            logger.error("Failed to update BOM with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update BOM: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBom(@PathVariable Integer id) {
        logger.info("Received request to delete BOM with id: {}", id);
        try {
            Bom deletedBom = bomService.deleteBom(id);

            BOMTemplateMapper responseMapper = new BOMTemplateMapper();
            responseMapper.setBom(deletedBom);

            logger.info("Successfully deleted BOM ID: {}", id);
            return ResponseEntity.status(HttpStatus.OK).body("Bom with id: "+id+" is deleted");
        } catch (Exception e) {
            logger.error("Failed to delete BOM ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete BOM: " + e.getMessage()));
        }
    }

    @GetMapping("/get-by-item/{itemId}")
    public ResponseEntity<?> getBomByItem(@PathVariable Integer itemId) {
        logger.debug("Received request to get BOMs by item ID: {}", itemId);
        try {
            List<Bom> boms = bomService.getBomByParentInventoryItem(itemId);
            List<BOMTemplateMapper> responseList = new ArrayList<>();

            for (Bom bom : boms) {
                WorkOrderProductionTemplate template = bomService.getBomWOTemplateByBomId(bom.getId());

                BOMTemplateMapper mapper = new BOMTemplateMapper();
                mapper.setBom(bom);
                mapper.setWorkOrderProductionTemplate(template);

                responseList.add(mapper);
            }

            logger.info("Returning {} BOM(s) for item ID: {}", responseList.size(), itemId);
            return ResponseEntity.ok(responseList);

        } catch (Exception e) {
            logger.error("Failed to fetch BOMs by item ID {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch BOMs: " + e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBoms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "") String search){
        logger.debug("Received request to fetch all BOMs with pagination and sorting");
        try {
            Page<Bom> items = bomService.getAllBom(page, size, sortBy, sortDir, search);
            Page<BomDTO> dtoPage = items.map(BomMapper::toDto);
            return ResponseEntity.ok(dtoPage);

        } catch (Exception e) {
            logger.error("Error fetching all BOMs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@PathVariable int id, @RequestParam("file") MultipartFile file) {
        try {

            bomService.saveAttachment(id, file);

            return ResponseEntity.ok("File uploaded successfully!");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<UrlResource> downloadFile(@PathVariable Long fileId) {
        try {
            UrlResource file = bomService.getAttachmentById(fileId);

            if (file == null || !file.exists()) {
                throw new ResourceNotFoundException("File not found with ID: " + fileId);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                    .body(file);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (MalformedURLException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @DeleteMapping("/delete/{fileId}")
    public ResponseEntity<String> deleteFile(@PathVariable Long fileId) {
        try {
            logger.info("Attempting to delete file with ID: {}", fileId);

            bomService.deleteAttachment(fileId);

            logger.info("File successfully deleted with ID: {}", fileId);
            return ResponseEntity.ok("File deleted successfully!");

        } catch (ResourceNotFoundException e) {
            logger.warn("File not found for deletion with ID: {}", fileId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found: " + e.getMessage());

        } catch (IOException e) {
            logger.error("Error deleting file with ID: {}. Message: {}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting file: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Unexpected error while deleting file with ID: {}. Message: {}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error occurred.");
        }
    }


    @PostMapping("/filter")
    public Page<BomListDTO> filterInventoryItems(@RequestBody FilterRequest request) {
        return bomService.filterBom(request);
    }

    @PostMapping("/active/search")
    public Page<BomListDTO> searchActiveBom(@RequestBody String request) {
        return bomService.searchActiveBom(request);
    }
}