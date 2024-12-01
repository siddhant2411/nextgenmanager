package com.nextgenmanager.nextgenmanager.bom.controller;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bom")
@CrossOrigin(origins = "http://localhost:3000")
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
}