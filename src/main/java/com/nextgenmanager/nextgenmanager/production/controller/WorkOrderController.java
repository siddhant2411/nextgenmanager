package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.production.dto.IssueWorkOrderMaterialDTO;
import com.nextgenmanager.nextgenmanager.production.dto.PartialOperationCompleteDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderListDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderRequestDTO;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/production/work-order")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderController.class);

    // ============================================================================
    // CREATE - POST /api/manufacturing/work-order
    // ============================================================================
    /**
     * Create a new Work Order
     *
     * @param dto Work Order creation request DTO
     * @return 201 Created with WorkOrderDTO, or 400 Bad Request on validation error
     *
     * Example Request Body:
     * {
     *   "bomId": 1,
     *   "routingId": 1,
     *   "plannedQuantity": 100,
     *   "dueDate": "2026-02-26",
     *   "sourceType": "SALES_ORDER",
     *   "salesOrder": { "id": 5 }
     * }
     */
    @PostMapping
    public ResponseEntity<?> createWorkOrder(@RequestBody WorkOrderRequestDTO dto) {
        try {
            logger.debug("Creating new WorkOrder with quantity: {}", dto.getPlannedQuantity());

            WorkOrderDTO created = workOrderService.addWorkOrder(dto);

            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for WorkOrder creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid input: " + e.getMessage()));

        } catch (EntityNotFoundException e) {
            logger.error("Referenced entity not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Entity not found: " + e.getMessage()));

        } catch (Exception e) {
            logger.error("Error creating WorkOrder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create WorkOrder: " + e.getMessage()));
        }
    }

    // ============================================================================
    // READ - GET /api/manufacturing/work-order/{id}
    // ============================================================================
    /**
     * Get a specific Work Order by ID
     *
     * @param id Work Order ID
     * @return 200 OK with WorkOrderDTO, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkOrder(@PathVariable int id) {
        try {
            logger.debug("Fetching WorkOrder with id: {}", id);

            WorkOrderDTO workOrder = workOrderService.getWorkOrder(id);

            return ResponseEntity.ok(workOrder);

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (Exception e) {
            logger.error("Error fetching WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch WorkOrder: " + e.getMessage()));
        }
    }

    // ============================================================================
    // READ ALL - GET /api/manufacturing/work-order/search
    // ============================================================================
    /**
     * Get all Work Orders with pagination and filtering
     *
     * Example: GET /api/manufacturing/work-order/search?page=0&size=20&sortBy=dueDate&sortDir=asc&query=WO-001
     */
    @PostMapping("/get-list")
    public ResponseEntity<?> getAllWorkOrders(
            @RequestBody FilterRequest filterRequest) {

        try {

            Page<WorkOrderListDTO> workOrders = workOrderService.getAllWorkOrders(filterRequest);

            return ResponseEntity.ok(workOrders);

        } catch (Exception e) {
            logger.error("Error fetching WorkOrders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch WorkOrders: " + e.getMessage()));
        }
    }

    // ============================================================================
    // UPDATE - PUT /api/manufacturing/work-order/{id}
    // ============================================================================
    /**
     * Update an existing Work Order
     *
     * Rules:
     * - Only updatable in CREATED status
     * - Cannot change BOM or Routing
     * - Cannot change quantity if operations/materials already exist
     * - Cannot change quantity if materials have been issued
     *
     * @param id Work Order ID
     * @param dto Updated Work Order data
     * @return 200 OK with updated WorkOrderDTO, or 400/404/409 on error
     *
     * Example Request Body:
     * {
     *   "plannedQuantity": 150,
     *   "dueDate": "2026-03-01",
     *   "remarks": "Updated quantity due to customer request"
     * }
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateWorkOrder(
            @PathVariable int id,
            @RequestBody WorkOrderRequestDTO dto) {

        try {
            logger.debug("Updating WorkOrder id: {} with new quantity: {}", id, dto.getPlannedQuantity());

            WorkOrderDTO updated = workOrderService.updateWorkOrder(id, dto);

            return ResponseEntity.ok(updated);

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (IllegalStateException e) {
            logger.warn("Update rejected for WorkOrder id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for WorkOrder update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid input: " + e.getMessage()));

        } catch (Exception e) {
            logger.error("Error updating WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update WorkOrder: " + e.getMessage()));
        }
    }

    // ============================================================================
    // STATE TRANSITIONS
    // ============================================================================

    /**
     * Release a Work Order (CREATED → RELEASED)
     *
     * Transitions work order from CREATED to RELEASED status.
     * After release, operations and materials can be created/managed.
     */
    @PatchMapping("/{id}/release")
    public ResponseEntity<?> releaseWorkOrder(@PathVariable int id) {
        try {
            logger.debug("Releasing WorkOrder id: {}", id);

            WorkOrderDTO released = workOrderService.releaseWorkOrder(id);

            return ResponseEntity.ok(released);

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (IllegalStateException e) {
            logger.warn("Cannot release WorkOrder id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error releasing WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to release WorkOrder: " + e.getMessage()));
        }
    }

    /**
     * Start an Operation
     * Transitions an operation to IN_PROGRESS status.
     */
    @PatchMapping("/operation/{operationId}/start")
    public ResponseEntity<?> startOperation(@PathVariable Long operationId) {
        try {
            logger.debug("Starting operation id: {}", operationId);

            workOrderService.startOperation(operationId);

            logger.info("Operation {} started successfully", operationId);
            return ResponseEntity.ok(Map.of("message", "Operation started successfully"));

        } catch (EntityNotFoundException e) {
            logger.warn("Operation not found with id: {}", operationId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Operation not found with ID: " + operationId));

        } catch (IllegalStateException e) {
            logger.warn("Cannot start operation id {}: {}", operationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error starting operation id: {}", operationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start operation: " + e.getMessage()));
        }
    }

    /**
     * Complete an Operation
     * Transitions an operation to COMPLETED status and records completed quantity.
     * Triggers material backflush if configured.
     */
    @PatchMapping("/operation/{operationId}/complete")
    public ResponseEntity<?> completeOperation(
            @PathVariable Long operationId,
            @RequestParam BigDecimal completedQty) {

        try {
            logger.debug("Completing operation id: {} with quantity: {}", operationId, completedQty);

            if (completedQty == null || completedQty.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Completed quantity must be greater than zero"));
            }

            workOrderService.completeOperation(operationId, completedQty);

            logger.info("Operation {} completed with quantity: {}", operationId, completedQty);
            return ResponseEntity.ok(Map.of("message", "Operation completed successfully"));

        } catch (EntityNotFoundException e) {
            logger.warn("Operation not found with id: {}", operationId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Operation not found with ID: " + operationId));

        } catch (IllegalStateException e) {
            logger.warn("Cannot complete operation id {}: {}", operationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error completing operation id: {}", operationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete operation: " + e.getMessage()));
        }
    }

    /**
     * Issue Materials for a Work Order
     * Supports partial material issuance.
     * Multiple calls can be made to issue materials incrementally.
     *
     * Example Request Body:
     * {
     *   "workOrderId": 1,
     *   "materials": [
     *     {
     *       "workOrderMaterialId": 5,
     *       "issuedQuantity": 50,
     *       "scrappedQuantity": 2
     *     },
     *     {
     *       "workOrderMaterialId": 6,
     *       "issuedQuantity": 30
     *     }
     *   ]
     * }
     */
    @PatchMapping("/material/issue")
    public ResponseEntity<?> issueMaterials(@RequestBody IssueWorkOrderMaterialDTO issueDTO) {
        try {
            logger.debug("Issuing materials for WorkOrder id: {} with {} material items",
                    issueDTO.getWorkOrderId(),
                    issueDTO.getMaterials().size());

            if (issueDTO.getMaterials() == null || issueDTO.getMaterials().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Materials list cannot be empty"));
            }

            // Validate each material item
//            for (IssueWorkOrderMaterialDTO.MaterialIssueItem item : issueDTO.getMaterials()) {
//                if (item.getIssuedQuantity() == null ||
//                        item.getIssuedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
//                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                            .body(Map.of("error", "Issued quantity must be greater than zero for all materials"));
//                }
//            }

            workOrderService.issueMaterials(issueDTO);

            logger.info("Materials issued successfully for WorkOrder id: {}", issueDTO.getWorkOrderId());
            return ResponseEntity.ok(Map.of(
                    "message", "Materials issued successfully",
                    "workOrderId", issueDTO.getWorkOrderId(),
                    "itemsIssued", issueDTO.getMaterials().size()
            ));

        } catch (EntityNotFoundException e) {
            logger.warn("Entity not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot issue materials: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input for material issuance: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error issuing materials", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to issue materials: " + e.getMessage()));
        }
    }

    /**
     * Complete Operation with Partial Quantity Support
     * Allows completing operations incrementally with partial quantities.
     * When total completed quantity meets planned quantity, operation is marked COMPLETED
     * and next operation is unlocked.
     *
     * Example Request Body:
     * {
     *   "operationId": 10,
     *   "completedQuantity": 25,
     *   "scrappedQuantity": 1,
     *   "remarks": "First batch completed"
     * }
     */
    @PatchMapping("/operation/{operationId}/complete-partial")
    public ResponseEntity<?> completeOperationPartial(
            @PathVariable Long operationId,
            @RequestBody PartialOperationCompleteDTO partialCompleteDTO) {

        try {
            logger.debug("Completing operation id: {} with partial quantity: {}",
                    operationId,
                    partialCompleteDTO.getCompletedQuantity());

            if (partialCompleteDTO.getCompletedQuantity() == null ||
                    partialCompleteDTO.getCompletedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Completed quantity must be greater than zero"));
            }

            // Set the operation ID from path variable
            partialCompleteDTO.setOperationId(operationId);

            workOrderService.completeOperationPartial(partialCompleteDTO);

            logger.info("Operation {} completed partially with quantity: {}",
                    operationId,
                    partialCompleteDTO.getCompletedQuantity());

            return ResponseEntity.ok(Map.of(
                    "message", "Operation completed partially",
                    "operationId", operationId,
                    "completedQuantity", partialCompleteDTO.getCompletedQuantity()
            ));

        } catch (EntityNotFoundException e) {
            logger.warn("Operation not found with id: {}", operationId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Operation not found with ID: " + operationId));

        } catch (IllegalStateException e) {
            logger.warn("Cannot complete operation id {}: {}", operationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input for operation completion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error completing operation id: {}", operationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete operation: " + e.getMessage()));
        }
    }

    /**
     * Complete a Work Order
     * Transitions work order to COMPLETED status.
     * All operations must be completed first.
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> completeWorkOrder(@PathVariable int id) {
        try {
            logger.debug("Completing WorkOrder id: {}", id);

            workOrderService.completeWorkOrder(id);

            logger.info("WorkOrder {} completed successfully", id);
            return ResponseEntity.ok(Map.of("message", "WorkOrder completed successfully"));

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (IllegalStateException e) {
            logger.warn("Cannot complete WorkOrder id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error completing WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete WorkOrder: " + e.getMessage()));
        }
    }

    /**
     * Close a Work Order
     * Transitions work order to CLOSED status.
     * No further changes allowed after closing.
     */
    @PatchMapping("/{id}/close")
    public ResponseEntity<?> closeWorkOrder(@PathVariable int id) {
        try {
            logger.debug("Closing WorkOrder id: {}", id);

            workOrderService.closeWorkOrder(id);

            logger.info("WorkOrder {} closed successfully", id);
            return ResponseEntity.ok(Map.of("message", "WorkOrder closed successfully"));

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (IllegalStateException e) {
            logger.warn("Cannot close WorkOrder id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error closing WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to close WorkOrder: " + e.getMessage()));
        }
    }

    /**
     * Cancel a Work Order
     * Transitions work order to CANCELLED status.
     * Reverses any inventory transactions if applicable.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelWorkOrder(@PathVariable int id) {
        try {
            logger.debug("Cancelling WorkOrder id: {}", id);

            workOrderService.cancelWorkOrder(id);

            logger.info("WorkOrder {} cancelled successfully", id);
            return ResponseEntity.ok(Map.of("message", "WorkOrder cancelled successfully"));

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (IllegalStateException e) {
            logger.warn("Cannot cancel WorkOrder id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error cancelling WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel WorkOrder: " + e.getMessage()));
        }
    }

    // ============================================================================
    // SOFT DELETE - DELETE /api/manufacturing/work-order/{id}
    // ============================================================================
    /**
     * Soft delete a Work Order
     * Marks work order as deleted (sets deletedDate) without removing from database.
     * Used for audit trail and compliance purposes.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWorkOrder(
            @PathVariable int id,
            @RequestParam(required = false, defaultValue = "No reason provided") String reason) {

        try {
            logger.debug("Soft deleting WorkOrder id: {} with reason: {}", id, reason);

            workOrderService.softDeleteWorkOrder(id, reason);

            logger.info("WorkOrder {} soft deleted successfully", id);
            return ResponseEntity.ok(Map.of("message", "WorkOrder deleted successfully"));

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (Exception e) {
            logger.error("Error deleting WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete WorkOrder: " + e.getMessage()));
        }
    }

    // ============================================================================
    // EXCEPTION HANDLERS
    // ============================================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        logger.error("Invalid argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid input: " + e.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleEntityNotFound(EntityNotFoundException e) {
        logger.error("Entity not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Entity not found: " + e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException e) {
        logger.error("Invalid operation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }
}