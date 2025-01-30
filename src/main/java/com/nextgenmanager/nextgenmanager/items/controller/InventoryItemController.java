package com.nextgenmanager.nextgenmanager.items.controller;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory_item")
@CrossOrigin(origins = {"http://localhost:3000","http://ec2-13-201-223-35.ap-south-1.compute.amazonaws.com"})
public class InventoryItemController {

    @Autowired
    private InventoryItemService inventoryItemService;

    private static final Logger logger = LoggerFactory.getLogger(InventoryItemController.class);

    @PostMapping("/add")
    public ResponseEntity<InventoryItem> addInventoryItem(@RequestBody InventoryItem inventoryItem) {
        logger.debug("Received request to add inventory item");
        try {
            InventoryItem savedItem = inventoryItemService.addInventoryItem(inventoryItem);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedItem);
        } catch (Exception e) {
            logger.error("Error adding inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryItem> getInventoryItem(@PathVariable String id) {
        logger.debug("Received request to fetch inventory item with id: {}", Integer.parseInt(id));
        try {
            InventoryItem inventoryItem = inventoryItemService.getInventoryItem(Integer.parseInt(id));
            return ResponseEntity.ok(inventoryItem);
        } catch (Exception e) {
            logger.error("Error fetching inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_EXTENDED).build();
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllInventoryItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "inventoryItemId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "") String search){
        logger.debug("Received request to fetch all active inventory items with pagination and sorting");
        try {
            Page<InventoryItem> items = inventoryItemService.getAllInventoryItems(page, size, sortBy, sortDir,search);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error fetching all inventory items: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/all-with-deleted")
    public ResponseEntity<List<InventoryItem>> getAllInventoryItemsWithDeleted() {
        logger.debug("Received request to fetch all inventory items including deleted");
        try {
            List<InventoryItem> items = inventoryItemService.getAllInventoryItemsWithDeleted();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error fetching inventory items with deleted: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_EXTENDED).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInventoryItem(@PathVariable int id) {
        logger.debug("Received request to delete inventory item with id: {}", id);
        try {
            inventoryItemService.deleteInventoryItem(id);
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (IllegalArgumentException e) {
            logger.warn("Item not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error deleting inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryItem> editInventoryItem(
            @PathVariable int id,
            @RequestBody InventoryItem updatedItem) {
        logger.debug("Received request to edit inventory item with id: {}", id);
        try {
            InventoryItem savedItem = inventoryItemService.editInventoryItem(id, updatedItem);
            return ResponseEntity.ok(savedItem);
        } catch (IllegalArgumentException e) {
            logger.warn("Item not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error editing inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public Page<InventoryItem> searchInventoryItems(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5"
            ) int size) {
        return inventoryItemService.searchInventoryItems(query, page, size);
    }

}
