package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.*;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import io.swagger.models.auth.In;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_INVENTORY_ADMIN','ROLE_INVENTORY_USER')")
@Validated
public class InventoryInstanceController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryInstanceController.class);

    @Autowired
    private InventoryInstanceService inventoryInstanceService;

    @PostMapping("/add")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<?> addInventoryInstance(
            @RequestBody InventoryInstance inventoryInstance,
            @RequestParam("qty") double qty) {

        logger.info("Received request to add inventory instance: {}, qty: {}", inventoryInstance, qty);

        try {
            // Validate inventory item
            if (inventoryInstance.getInventoryItem() == null ||
                inventoryInstance.getInventoryItem().getInventoryItemId() <= 0) {
                logger.warn("Inventory item is missing or invalid in the request.");
                return ResponseEntity.badRequest().body("Valid inventory item must be provided.");
            }

            // Fetch full item details
            InventoryItem item = inventoryInstance.getInventoryItem();

            // Use the new createInstances method
            List<InventoryInstance> createdInstances =
                    inventoryInstanceService.createInstances(item, qty, inventoryInstance);

            logger.info("Successfully created {} inventory instance(s) for item ID: {}",
                    createdInstances.size(), item.getInventoryItemId());

            return ResponseEntity.status(HttpStatus.CREATED).body(createdInstances);

        } catch (NumberFormatException e) {
            logger.error("Invalid quantity format: {}", qty, e);
            return ResponseEntity.badRequest().body("Invalid quantity format.");
        } catch (Exception e) {
            logger.error("Error creating inventory instances: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating inventory instances: " + e.getMessage());
        }
    }

    @PostMapping("/consume")
    public ResponseEntity<?> consumeQuantity(
            @RequestBody InventoryItem inventoryItem, @RequestParam("qty") double consumeQty, Long requestId) {
        logger.info("Received request to consume inventory for item ID: {}, qty: {}", inventoryItem.getInventoryItemId(), consumeQty);
        try {

            if (consumeQty <= 0) {
                logger.warn("Invalid consumption quantity provided: {}", consumeQty);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Quantity to consume must be greater than zero.");
            }

            // Call the service to consume inventory instances
            List<InventoryInstance> consumedItems = inventoryInstanceService.consumeInventoryInstance(inventoryItem, consumeQty,requestId);
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


    @PostMapping("/book")
    public ResponseEntity<?> bookQuantity(
            @RequestBody InventoryItem inventoryItem, @RequestParam("qty") double consumeQty) {
        logger.info("Received request to consume inventory for item ID: {}, qty: {}", inventoryItem.getInventoryItemId(), consumeQty);
        try {

            if (consumeQty <= 0) {
                logger.warn("Invalid consumption quantity provided: {}", consumeQty);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Quantity to consume must be greater than zero.");
            }

            // Call the service to consume inventory instances
            List<InventoryInstance> consumedItems = inventoryInstanceService.bookInventoryInstance(inventoryItem, consumeQty);
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

    @GetMapping("/grouped")
    public Page<GroupedInventoryItem> getGroupedInventory(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam(defaultValue = "inventoryItemId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String hsnCode,
            @RequestParam(required = false) Double totalQuantityCondition,
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) UOM uom,
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) InventoryApprovalStatus inventoryApprovalStatus,
            @RequestParam(required = false) ProcurementDecision procurementDecisionFilter
    ) {
        return inventoryInstanceService.getGroupedInventoryInstances(
                page, size, sortBy, sortDir, itemCode, name, hsnCode,
                totalQuantityCondition, filterType, uom, itemType,inventoryApprovalStatus,procurementDecisionFilter
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
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


//    @PostMapping("/arrival")
//    public ResponseEntity<?> markInventoryAsArrived(@RequestBody List<Long> instanceIds) {
//        List<InventoryInstance> arrived = inventoryInstanceService.markRequestedInventoryAsArrived(instanceIds);
//        return ResponseEntity.ok(arrived);
//    }

    @GetMapping("/summary")
    public Map<String, Object> getInventorySummary() {

        return inventoryInstanceService.getInventorySummary();
    }

    @PostMapping("/requests")
    public ResponseEntity<?> requestInventory(
            @RequestParam("itemId") int itemId,
            @RequestParam("quantity") @Min(1) double quantity,
            @RequestParam(value = "source", defaultValue = "MANUAL") InventoryRequestSource source,
            @RequestParam(value = "sourceId", required = false) Long sourceId,
            @RequestParam(value = "requestedBy") String requestedBy,
            @RequestParam(value = "requestRemarks") String requestRemarks
    ) {
        try {
            InventoryRequest result = inventoryInstanceService.requestInstanceByItemId(itemId, quantity, source, sourceId,requestedBy,requestRemarks);
            return ResponseEntity.ok(result);
        }
        catch (Exception e){
            return ResponseEntity.badRequest().body("Something went wrong");
        }

    }

    @PutMapping("/requests/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<?> approveRequests(@RequestParam String requestId,  @RequestParam String approvedBy,  @RequestParam String approvalRemarks) {
        try {
            List<InventoryInstance> approved = inventoryInstanceService.approveInventoryRequest(Long.parseLong(requestId), approvedBy, approvalRemarks);
            return ResponseEntity.ok(approved);
        }
        catch (IllegalStateException e){
            return ResponseEntity.badRequest().body("Some Items are still Pending");
        }
        catch (Exception e){
            return ResponseEntity.badRequest().body("Something went wrong");
        }
    }

    @PutMapping("/requests/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<List<InventoryInstance>> rejectRequest(@RequestParam String requestId,@RequestParam String approvedBy, @RequestParam String approvalRemarks) {
        List<InventoryInstance> approved = inventoryInstanceService.rejectInventoryRequest(Long.parseLong(requestId),approvedBy,approvalRemarks);
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/add-instances")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<?> addInventory(@RequestBody AddInventoryRequest request) {
        try {
            List<InventoryInstance> savedInstances = inventoryInstanceService.addInventory(request);
            return ResponseEntity.ok(savedInstances);

        } catch (ResourceNotFoundException ex) {
            // Custom exception for missing inventory item
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));

        } catch (IllegalArgumentException ex) {
            // Bad input such as zero or negative quantity
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            // Any other unhandled server error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add inventory: " + ex.getMessage()));
        }
    }


    @GetMapping("/requests/grouped")
    public ResponseEntity<Page<InventoryRequestGroupDTO>> getGroupedRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) InventoryRequestSource source,
            @RequestParam(required = false) InventoryApprovalStatus approvalStatus,
            @RequestParam(required = false) Long referenceId
    ) {
        Page<InventoryRequestGroupDTO> groupedPage = inventoryInstanceService.getGroupedRequests(
                page, size, itemCode, itemName, source, approvalStatus, referenceId
        );
        return ResponseEntity.ok(groupedPage);
    }

    @GetMapping("/requests/grouped/detail")
    public ResponseEntity<List<InventoryInstance>> getGroupDetails(
            @RequestParam Long referenceId
    ) {
        List<InventoryInstance> instances =
                inventoryInstanceService.getRequestedInstancesByReferenceAndItem(referenceId);

        return ResponseEntity.ok(instances);
    }

    @PutMapping("/inventory-procurement/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<Void> changeProcurementStatus(@PathVariable Long id,
                                                        @RequestParam InventoryProcurementStatus status) {
        inventoryInstanceService.updateProcurementStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/inventory-procurement-orders")
    public Page<InventoryProcurementOrderDTO> getProcurementOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) InventoryProcurementStatus status,
            @RequestParam(required = false) Long inventoryItemId,
            @RequestParam(required = false) String createdBy
    ) {
        return inventoryInstanceService.getProcurementOrders(page, size, status, inventoryItemId, createdBy);
    }

    /**
     * Add inventory to an existing procurement order.
     *
     * @param procurementOrderId ID of the existing procurement order
     * @param request            AddInventoryRequest containing itemId, quantity, costPerUnit, referenceId, createdBy, etc.
     * @return List of InventoryInstance added (updated + new)
     */
    @PostMapping("/procurement/{procurementOrderId}/add")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<List<InventoryInstance>> addInventoryToExistingProcurement(
            @PathVariable("procurementOrderId") long procurementOrderId,
            @RequestBody AddInventoryRequest request) {

        List<InventoryInstance> updatedInstances =
                inventoryInstanceService.addInventoryToExistingProcurement(request, procurementOrderId);

        return ResponseEntity.ok(updatedInstances);
    }

    @PutMapping("/inventory-procurement-orders/{orderId}/complete")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<?> completeProcurementOrder(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "system") String completedBy) {
        try {
            InventoryProcurementOrderDTO dto = inventoryInstanceService.completeProcurementOrder(orderId, completedBy);
            return ResponseEntity.ok(dto);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + ex.getMessage());
        }
    }


}

