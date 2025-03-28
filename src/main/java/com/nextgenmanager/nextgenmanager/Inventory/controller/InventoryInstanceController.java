package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryPresentDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import io.swagger.models.auth.In;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")

public class InventoryInstanceController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryInstanceController.class);

    @Autowired
    private InventoryInstanceService inventoryInstanceService;

    @PostMapping("/add")
    public ResponseEntity<?> addInventoryInstance(
            @RequestBody InventoryInstance inventoryInstance, @RequestParam("qty") double qty) {
        logger.info("Received request to add inventory instance: {}, qty: {}", inventoryInstance, qty);
        try {
            // Validate inventory item
            if (inventoryInstance.getInventoryItem() == null) {
                logger.warn("Inventory item is missing in the request.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Inventory item must be provided.");
            }

            // Call the service to create instances
            inventoryInstanceService.createInventoryInstances(inventoryInstance, qty);
            logger.info("Inventory instances successfully created for item ID: {} with quantity: {}",
                    inventoryInstance.getInventoryItem().getInventoryItemId(), qty);
            return ResponseEntity.status(HttpStatus.CREATED).body("Inventory instances created successfully.");
        } catch (NumberFormatException e) {
            logger.error("Invalid quantity provided: {}", qty, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid quantity provided.");
        } catch (Exception e) {
            logger.error("An error occurred while creating inventory instances.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while creating inventory instances: " + e.getMessage());
        }
    }

    @PostMapping("/consume")
    public ResponseEntity<?> consumeQuantity(
            @RequestBody InventoryItem inventoryItem, @RequestParam("qty") double consumeQty) {
        logger.info("Received request to consume inventory for item ID: {}, qty: {}", inventoryItem.getInventoryItemId(), consumeQty);
        try {

            if (consumeQty <= 0) {
                logger.warn("Invalid consumption quantity provided: {}", consumeQty);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Quantity to consume must be greater than zero.");
            }

            // Call the service to consume inventory instances
            List<InventoryInstance> consumedItems = inventoryInstanceService.consumeInventoryInstance(inventoryItem, consumeQty);
            logger.info("Successfully consumed inventory instances for item ID: {} with quantity: {}", inventoryItem.getInventoryItemId(), consumeQty);
            return ResponseEntity.status(HttpStatus.OK).body(consumedItems);
        } catch (IllegalArgumentException e) {
            logger.error("Error during consumption of inventory instances.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid request: " + e.getMessage());
        } catch (Exception e) {
            logger.error("An error occurred while consuming inventory instances.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while consuming inventory instances: " + e.getMessage());
        }
    }
    @GetMapping("/present")
    public ResponseEntity<?> getPresentInventoryInstances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "inventoryItemRef") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) String hsnCode,
            @RequestParam(required = false, defaultValue = "0") Double totalQty,  // Changed to Double
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) UOM uom,
            @RequestParam(required = false)ItemType itemType) {
        try {
            logger.info("Fetching present inventory instances. Page: {}, Size: {}, SortBy: {}, SortDir: {}",
                    page, size, sortBy, sortDir);

            // Call the service method to fetch paginated inventory instances
            Page<InventoryPresentDTO> paginatedInventoryInstances =
                    inventoryInstanceService.getPresentInventoryInstances(page, size, sortBy, sortDir, itemCode,itemName,hsnCode,totalQty,filterType,uom,itemType);

            // Wrap the results in a response body
            return ResponseEntity.ok(paginatedInventoryInstances);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching present inventory instances: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching present inventory instances: " + e.getMessage());
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInventoryInstance(@PathVariable String id) {
        logger.info("Received request to delete inventory instance with id: {}", id);
        try {
            inventoryInstanceService.deleteInventoryInstance(Long.parseLong(id));
            return ResponseEntity.status(HttpStatus.OK).body("Inventory Instance deleted successfully");
        }
        catch (ResourceNotFoundException e){
            logger.error("Inventory Instance does not exist: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to delete Inventory Instance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }catch (Exception e) {
            logger.error("Error deleting BOM: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to delete BOM: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateInventoryInstance(@PathVariable String id, @RequestBody InventoryInstance inventoryInstance) {
        logger.info("Received request to update BOM with id: {}", id);
        try {
            inventoryInstance.setId(Long.parseLong(id));
            InventoryInstance updateInventoryInstance = inventoryInstanceService.updateInventoryInstance(inventoryInstance);
            return ResponseEntity.ok(updateInventoryInstance);
        }  catch (ResourceNotFoundException e){
            logger.error("Inventory Instance does not exist: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to update Inventory Instance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Error updating Inventory Instance: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to update Inventory Instance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getInventoryInstance(@PathVariable String id) {
        logger.info("Received request to update BOM with id: {}", id);
        try {

            InventoryInstance inventoryInstance = inventoryInstanceService.getInventoryInstanceById(Long.parseLong(id));
            return ResponseEntity.ok(inventoryInstance);
        }  catch (ResourceNotFoundException e){
            logger.error("Inventory Instance does not exist: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to get Inventory Instance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Error get Inventory Instance: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to update Inventory Instance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

}
