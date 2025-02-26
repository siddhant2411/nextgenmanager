package com.nextgenmanager.nextgenmanager.bom.controller;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItemAttachment;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/bom")
@CrossOrigin(origins = {"http://localhost:3000","http://ec2-13-201-223-35.ap-south-1.compute.amazonaws.com"})
public class BomController {

    private static final Logger logger = LoggerFactory.getLogger(BomController.class);

    @Autowired
    private BomService bomService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getBom(@PathVariable String id) {
        logger.info("Received request to fetch BOM with id: {}", id);
        try {
            Bom bom = bomService.getBom(Integer.parseInt(id));
            return ResponseEntity.ok(bom);
        } catch (ResourceNotFoundException e) {
            logger.error("BOM not found or invalid: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Internal server error occurred");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping
    public ResponseEntity<?> addBom(@RequestBody Bom bom) {
        logger.info("Received request to add new BOM");
        try {
            Bom savedBom = bomService.addBom(bom);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedBom);
        } catch (Exception e) {
            logger.error("Error creating BOM: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to create BOM: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBom(@PathVariable String id, @RequestBody Bom bom) {
        logger.info("Received request to update BOM with id: {}", id);
        try {
            bom.setId(Integer.parseInt(id));
            Bom updatedBom = bomService.editBom(bom);
            return ResponseEntity.ok(updatedBom);
        } catch (Exception e) {
            logger.error("Error updating BOM: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to update BOM: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBom(@PathVariable String id) {
        logger.info("Received request to delete BOM with id: {}", id);
        try {
            Bom deletedBom = bomService.deleteBom(Integer.parseInt(id));
            return ResponseEntity.ok(deletedBom);
        } catch (Exception e) {
            logger.error("Error deleting BOM: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to delete BOM: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
            Page<BomDTO> items = bomService.getAllBom(page, size, sortBy, sortDir,search);
            return ResponseEntity.ok(items);
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


}