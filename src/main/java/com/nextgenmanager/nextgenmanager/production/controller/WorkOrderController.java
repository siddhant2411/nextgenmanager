package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import com.nextgenmanager.nextgenmanager.production.service.TestTemplateService;
import com.nextgenmanager.nextgenmanager.production.dto.RejectionEntryDTO;
import com.nextgenmanager.nextgenmanager.production.dto.YieldMetricsDTO;
import com.nextgenmanager.nextgenmanager.production.enums.DispositionStatus;
import com.nextgenmanager.nextgenmanager.production.service.RejectionService;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderService;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.MachineScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/work-order")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
@Tag(
    name = "Work Orders",
    description = "APIs for managing manufacturing work orders. Includes creation, retrieval, state transitions, material issuance, and scheduling."
)
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private WorkOrderExportService workOrderExportService;

    @Autowired
    private RejectionService rejectionService;

    @Autowired
    private TestTemplateService testTemplateService;

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderController.class);

    // ============================================================================
    // CREATE - POST /api/production/work-order
    // ============================================================================
    @Operation(
        summary = "Create a new Work Order",
        description = "Creates a new work order for a specified BOM and routing. Automatically generates materials and operations from the BOM/Routing structure. Work order starts in CREATED status.",
        tags = {"Create"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Work Order created successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input: BOM ID is required, Planned quantity must be > 0, or BOM has no materials/operations",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{\"error\": \"Invalid input: ...\"}")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Referenced entity not found: BOM or Routing not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{\"error\": \"Entity not found: ...\"}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error while creating work order",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<?> createWorkOrder(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Work Order creation request with BOM, routing, quantity, and dates",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderRequestDTO.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    name = "Create Work Order Example",
                    value = "{\"bomId\": 1, \"plannedQuantity\": 100, \"dueDate\": \"2026-03-15\", \"sourceType\": \"SALES_ORDER\", \"salesOrder\": {\"id\": 5}}"
                )
            )
        )
        @RequestBody WorkOrderRequestDTO dto) {
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
    // READ - GET /api/production/work-order/{id}
    // ============================================================================
    @Operation(
        summary = "Get a Work Order by ID",
        description = "Retrieves a specific work order with all details including materials, operations, and related entities (BOM, Routing, Sales Order).",
        tags = {"Read"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order found and returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found with the specified ID",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{\"error\": \"WorkOrder not found with ID: 999\"}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error while fetching work order",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
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
    // READ ALL - POST /api/production/work-order/get-list
    // ============================================================================
    @Operation(
        summary = "Get all Work Orders with pagination and filtering",
        description = "Retrieves a paginated list of work orders with dynamic filtering, sorting, and search. Supports complex filter criteria with multiple conditions.",
        tags = {"Read"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Orders list retrieved successfully with pagination metadata",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Page.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid filter criteria or pagination parameters",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error while fetching work orders",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/get-list")
    public ResponseEntity<?> getAllWorkOrders(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Filter request with pagination, sorting, and filter criteria. Supports dynamic field filtering with operators like =, !=, contains, >, <, etc.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = FilterRequest.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    name = "Filter Work Orders Example",
                    value = "{\"page\": 0, \"size\": 20, \"sortBy\": \"dueDate\", \"sortDir\": \"asc\", \"filters\": [{\"fieldName\": \"status\", \"operator\": \"=\", \"value\": \"RELEASED\"}]}"
                )
            )
        )
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
    // UPDATE - PUT /api/production/work-order/{id}
    // ============================================================================
    @Operation(
        summary = "Update an existing Work Order",
        description = "Updates a work order with constraints. Work order must be in CREATED status. BOM and Routing cannot be changed. Quantity changes restricted if operations/materials exist or materials issued.",
        tags = {"Update"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order updated successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input or constraint violation (quantity > 0, cannot change BOM/Routing, etc.)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{\"error\": \"Invalid input: ...\"}")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found with the specified ID",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "State conflict: Work order not in CREATED status or constraints violated",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{\"error\": \"Cannot update WorkOrder - not in CREATED status\"}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error while updating work order",
            content = @Content(mediaType = "application/json")
        )
    })
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> updateWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Updated work order data. Can update: plannedQuantity, dueDate, remarks, priority. Cannot update: bomId, routingId.",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderRequestDTO.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    name = "Update Work Order Example",
                    value = "{\"plannedQuantity\": 150, \"dueDate\": \"2026-03-01\", \"remarks\": \"Updated quantity\"}"
                )
            )
        )
        @Validated
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

    @Operation(
        summary = "Release a Work Order (CREATED → RELEASED)",
        description = "Transitions work order from CREATED to RELEASED status. After release, operations and materials can be managed and production can begin.",
        tags = {"State Transitions"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order released successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Cannot release - Work Order not in CREATED status",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{\"error\": \"Work order must be in CREATED status to release\"}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @PatchMapping("/{id}/release")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> releaseWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id,
        @RequestParam(defaultValue = "false", required = false) boolean forceRelease) {
        try {
            logger.debug("Releasing WorkOrder id: {}", id);

            WorkOrderDTO released = workOrderService.releaseWorkOrder(id, forceRelease);

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
    @SecurityRequirement(name = "bearerAuth")
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
    @SecurityRequirement(name = "bearerAuth")
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
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
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
            for (IssueWorkOrderMaterialDTO.MaterialIssueItem item : issueDTO.getMaterials()) {
                if (item.getIssuedQuantity() == null ||
                        item.getIssuedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Issued quantity must be greater than zero for all materials"));
                }
            }

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
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> completeOperationPartial(
            @PathVariable Long operationId,
            @RequestBody PartialOperationCompleteDTO partialCompleteDTO) {

        try {
            logger.debug("Completing operation id: {} with partial quantity: {}",
                    operationId,
                    partialCompleteDTO.getCompletedQuantity());

            // Set the operation ID from path variable
            partialCompleteDTO.setOperationId(operationId);

            List<String> warnings = workOrderService.completeOperationPartial(partialCompleteDTO);

            logger.info("Operation {} completed partially with quantity: {}",
                    operationId,
                    partialCompleteDTO.getCompletedQuantity());

            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("message", "Operation completed partially");
            body.put("operationId", operationId);
            body.put("completedQuantity", partialCompleteDTO.getCompletedQuantity());
            if (!warnings.isEmpty()) body.put("warnings", warnings);
            return ResponseEntity.ok(body);

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

    // ─── Material Re-order ───────────────────────────────────────────────────────

    @PostMapping("/{workOrderId}/materials/{materialId}/reorder")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> reorderMaterial(
            @PathVariable int workOrderId,
            @PathVariable Long materialId,
            @RequestBody com.nextgenmanager.nextgenmanager.production.dto.ReorderMaterialRequestDTO dto) {
        try {
            com.nextgenmanager.nextgenmanager.production.dto.WorkOrderMaterialReorderDTO result =
                    workOrderService.reorderMaterial((long) workOrderId, materialId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating material re-order for WO {} material {}", workOrderId, materialId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{workOrderId}/materials/{materialId}/reorders")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getMaterialReorders(
            @PathVariable int workOrderId,
            @PathVariable Long materialId) {
        try {
            List<com.nextgenmanager.nextgenmanager.production.dto.WorkOrderMaterialReorderDTO> result =
                    workOrderService.getMaterialReorders((long) workOrderId, materialId);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching re-orders for WO {} material {}", workOrderId, materialId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Yield Metrics ──────────────────────────────────────────────────────────

    @GetMapping("/{workOrderId}/yield")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getYieldMetrics(@PathVariable int workOrderId) {
        try {
            YieldMetricsDTO dto = rejectionService.getYieldMetrics(workOrderId);
            return ResponseEntity.ok(dto);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching yield metrics for WO {}", workOrderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Rejection Entries ──────────────────────────────────────────────────────

    @GetMapping("/{workOrderId}/rejections")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> listRejections(
            @PathVariable int workOrderId,
            @RequestParam(required = false) DispositionStatus status) {
        try {
            List<RejectionEntryDTO> list = rejectionService.listRejections(workOrderId, status);
            return ResponseEntity.ok(list);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error listing rejections for WO {}", workOrderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
        summary = "Complete a Work Order (RELEASED → COMPLETED)",
        description = "Transitions work order to COMPLETED status. All operations must be completed first. Used when production is finished.",
        tags = {"State Transitions"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order completed successfully",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Cannot complete - pending operations exist or work order not in RELEASED status",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @PatchMapping("/{id}/complete")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> completeWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
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

    @Operation(
        summary = "Close a Work Order (COMPLETED → CLOSED)",
        description = "Transitions work order to CLOSED status. No further changes allowed. Used for final archival after production complete.",
        tags = {"State Transitions"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order closed successfully",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Cannot close - work order not in COMPLETED status",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @PatchMapping("/{id}/close")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> closeWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
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

    @Operation(
        summary = "Cancel a Work Order",
        description = "Transitions work order to CANCELLED status. Reverses any issued materials and inventory transactions. Requires ROLE_PRODUCTION_ADMIN or higher.",
        tags = {"State Transitions"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order cancelled successfully, inventory reversed",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions - requires ROLE_PRODUCTION_ADMIN",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Cannot cancel - work order in final state (CLOSED)",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> cancelWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
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
    // SHORT CLOSE - PATCH /api/production/work-order/{id}/short-close
    // ============================================================================
    @Operation(
        summary = "Short-close a Work Order (RELEASED/IN_PROGRESS → SHORT_CLOSED)",
        description = "Short-closes a work order before full completion. Accepts partial output, "
                + "returns unused issued materials back to store, cancels remaining inventory "
                + "reservations, and computes actual cost for the partial output. "
                + "Common in Indian MSME scenarios like tool breakage, priority changes, or material shortages. "
                + "Requires ROLE_PRODUCTION_ADMIN or higher.",
        tags = {"State Transitions"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order short-closed successfully. Partial output accepted and unused materials returned.",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Cannot short-close - Work Order not in RELEASED or IN_PROGRESS status",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @PatchMapping("/{id}/short-close")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> shortCloseWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id,
        @Parameter(
            name = "remarks",
            description = "Reason for short closure (e.g. 'Tool breakage', 'Priority changed', 'Material shortage')",
            required = false,
            example = "Tool breakage on machine M-03"
        )
        @RequestParam(required = false) String remarks) {
        try {
            logger.debug("Short-closing WorkOrder id: {} with remarks: {}", id, remarks);

            workOrderService.shortCloseWorkOrder(id, remarks);

            logger.info("WorkOrder {} short-closed successfully", id);
            return ResponseEntity.ok(Map.of(
                    "message", "WorkOrder short-closed successfully",
                    "workOrderId", id,
                    "remarks", remarks != null ? remarks : ""
            ));

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (IllegalStateException e) {
            logger.warn("Cannot short-close WorkOrder id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Error short-closing WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to short-close WorkOrder: " + e.getMessage()));
        }
    }

    // ============================================================================
    // SOFT DELETE - DELETE /api/production/work-order/{id}
    // ============================================================================
    @Operation(
        summary = "Soft delete a Work Order",
        description = "Soft deletes a work order by setting deletedDate without removing from database. Maintains full audit trail. Requires ROLE_PRODUCTION_ADMIN or higher.",
        tags = {"Delete"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order soft deleted successfully",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions - requires ROLE_PRODUCTION_ADMIN",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> deleteWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id,
        @Parameter(
            name = "reason",
            description = "Reason for deletion (optional)",
            example = "Duplicate order"
        )
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


    @Operation(
        summary = "Get Work Order History",
        description = "Retrieves the complete change history and audit trail for a work order, including all status changes, quantity updates, and material issuances.",
        tags = {"Read"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "History retrieved successfully",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @GetMapping("/{id}/history")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getWorkOrderHistory(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
        try {
            logger.debug("Fetching history for WorkOrder id: {}", id);

            var history = workOrderService.getWorkOrderHistory(id);

            return ResponseEntity.ok(history);

        } catch (EntityNotFoundException e) {
            logger.warn("WorkOrder not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "WorkOrder not found with ID: " + id));

        } catch (Exception e) {
            logger.error("Error fetching history for WorkOrder id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch WorkOrder history: " + e.getMessage()));
        }
    }

    @Operation(
        summary = "Get Work Order Summary",
        description = "Retrieves a summary of all work orders grouped by status with counts and key metrics. Useful for dashboard and reporting.",
        tags = {"Read"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Summary retrieved successfully",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @GetMapping("/summary")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getWorkOrderSummary() {
        try {
            logger.debug("Fetching Work Order summary");

            var summary = workOrderService.getWorkOrderSummary();

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            logger.error("Error fetching Work Order summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch Work Order summary: " + e.getMessage()));
        }
    }

    // ============================================================================
    // SCHEDULING ENDPOINTS
    // ============================================================================

    @Operation(
        summary = "Schedule a Work Order (Auto Scheduling)",
        description = "Automatically schedules a work order using forward scheduling algorithm. Allocates operations to work centers/machines based on capacity and constraints.",
        tags = {"Scheduling"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order scheduled successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ScheduleResultDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Cannot schedule - work order not in RELEASED status or no routing available",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/{id}/schedule")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> scheduleWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
        try {
            logger.debug("Scheduling WorkOrder id: {}", id);
            ScheduleResultDTO result = workOrderService.scheduleWorkOrder(id);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error scheduling WorkOrder {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to schedule: " + e.getMessage()));
        }
    }

    @Operation(
        summary = "Reschedule a Work Order",
        description = "Reschedules a work order with a new start date. Recalculates operation schedule based on new start date and resource constraints.",
        tags = {"Scheduling"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Work Order rescheduled successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ScheduleResultDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Cannot reschedule - work order in final status or scheduling conflict",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/reschedule")
    public ResponseEntity<?> rescheduleWorkOrder(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id,
        @Parameter(
            name = "startDate",
            description = "New start date in yyyy-MM-dd format",
            required = true,
            example = "2026-03-20"
        )
        @RequestParam @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date startDate) {
        try {
            logger.debug("Rescheduling WorkOrder id: {} with start date: {}", id, startDate);
            ScheduleResultDTO result = workOrderService.rescheduleWorkOrder(id, startDate);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error rescheduling WorkOrder {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reschedule: " + e.getMessage()));
        }
    }

    @Operation(
        summary = "Batch Schedule All Work Orders",
        description = "Batch schedules all CREATED work orders in priority order. Uses multi-criteria scheduling algorithm (FIFO, priority, due date). Useful for shift planning.",
        tags = {"Scheduling"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Batch scheduling completed",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = java.util.List.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error during batch scheduling",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/schedule-all")
    public ResponseEntity<?> scheduleAll() {
        try {
            logger.debug("Batch scheduling all CREATED work orders");
            java.util.List<ScheduleResultDTO> results = productionSchedulerService.scheduleAll();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error in batch scheduling", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Batch scheduling failed: " + e.getMessage()));
        }
    }

    // ============================================================================
    // MACHINE SCHEDULE ENDPOINTS
    // ============================================================================

    @Autowired
    private MachineScheduleService machineScheduleService;

    @Autowired
    private com.nextgenmanager.nextgenmanager.production.service.scheduling.ProductionSchedulerService productionSchedulerService;

    @Operation(
        summary = "Get Machine Task Queue",
        description = "Retrieves the work order task queue for a specific machine within a date range. Shows scheduled operations in sequence.",
        tags = {"Machine Schedule"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Machine schedule retrieved",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Machine not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/machine/{machineId}/schedule")
    public ResponseEntity<?> getMachineSchedule(
        @Parameter(
            name = "machineId",
            description = "Machine ID",
            required = true,
            example = "1"
        )
        @PathVariable Long machineId,
        @Parameter(
            name = "from",
            description = "Start date (yyyy-MM-dd format). Defaults to today.",
            example = "2026-03-01"
        )
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate from,
        @Parameter(
            name = "to",
            description = "End date (yyyy-MM-dd format). Defaults to 30 days from start date.",
            example = "2026-03-31"
        )
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate to) {
        try {
            if (from == null) from = java.time.LocalDate.now();
            if (to == null) to = from.plusDays(30);
            return ResponseEntity.ok(machineScheduleService.getMachineSchedule(machineId, from, to));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
        summary = "Get Today's Machine Schedule",
        description = "Retrieves today's task queue for a machine. Optimized view for operators showing current work assignments.",
        tags = {"Machine Schedule"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Today's schedule retrieved",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Machine not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/machine/{machineId}/schedule/today")
    public ResponseEntity<?> getMachineScheduleToday(
        @Parameter(
            name = "machineId",
            description = "Machine ID",
            required = true,
            example = "1"
        )
        @PathVariable Long machineId) {
        try {
            return ResponseEntity.ok(machineScheduleService.getMachineScheduleToday(machineId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================================
    // PRODUCTION SCHEDULE VIEW ENDPOINTS (PLANT MANAGER LEVEL)
    // ============================================================================

    @Autowired
    private com.nextgenmanager.nextgenmanager.production.service.scheduling.ProductionScheduleViewService productionScheduleViewService;

    @Operation(
        summary = "Get Combined Plant Schedule",
        description = "Retrieves complete plant schedule with all work centers and machines combined. Useful for plant manager level planning and capacity analysis.",
        tags = {"Production Schedule"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Combined schedule retrieved",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/schedule/combined")
    public ResponseEntity<?> getCombinedSchedule(
        @Parameter(
            name = "from",
            description = "Start date (yyyy-MM-dd format). Defaults to today.",
            example = "2026-03-01"
        )
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate from,
        @Parameter(
            name = "to",
            description = "End date (yyyy-MM-dd format). Defaults to 30 days from start date.",
            example = "2026-03-31"
        )
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate to) {
        try {
            if (from == null) from = java.time.LocalDate.now();
            if (to == null) to = from.plusDays(30);
            return ResponseEntity.ok(productionScheduleViewService.getCombinedSchedule(from, to));
        } catch (Exception e) {
            logger.error("Error fetching combined schedule", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get schedule for a specific Work Center (groups by machine internally)
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/schedule/work-center/{workCenterId}")
    public ResponseEntity<?> getWorkCenterSchedule(
            @PathVariable Integer workCenterId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate to) {
        try {
            if (from == null) from = java.time.LocalDate.now();
            if (to == null) to = from.plusDays(30);
            return ResponseEntity.ok(productionScheduleViewService.getWorkCenterSchedule(workCenterId, from, to));
        } catch (Exception e) {
            logger.error("Error fetching work center schedule for id {}", workCenterId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a detailed schedule for a specific machine using the new DTO
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/schedule/machine/{machineId}")
    public ResponseEntity<?> getMachineScheduleDetailed(
            @PathVariable Long machineId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate to) {
        try {
            if (from == null) from = java.time.LocalDate.now();
            if (to == null) to = from.plusDays(30);
            return ResponseEntity.ok(productionScheduleViewService.getMachineSchedule(machineId, from, to));
        } catch (Exception e) {
            logger.error("Error fetching detailed machine schedule for id {}", machineId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================================
    // QC TESTING ENDPOINTS
    // ============================================================================

    @Operation(
        summary = "Get QC Test Results for Work Order",
        description = "Retrieves all QC test results associated with a work order. Shows test templates and recorded results/measurements.",
        tags = {"QC Testing"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Test results retrieved",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = java.util.List.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/tests")
    public ResponseEntity<?> getWorkOrderTests(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
        try {
            List<WorkOrderTestResultDTO> results = testTemplateService.getTestResultsForWorkOrder(id);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching tests for WorkOrder {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
        summary = "Record/Update QC Test Result",
        description = "Records or updates a QC test result for a specific test. Captures measurement values, pass/fail status, and comments.",
        tags = {"QC Testing"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Test result recorded successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderTestResultDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Test result or work order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}/tests/{testResultId}")
    public ResponseEntity<?> recordTestResult(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id,
        @Parameter(
            name = "testResultId",
            description = "Test Result ID",
            required = true,
            example = "1"
        )
        @PathVariable Long testResultId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Test result with measurement values and status",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkOrderTestResultDTO.class)
            )
        )
        @RequestBody WorkOrderTestResultDTO dto) {
        try {
            WorkOrderTestResultDTO updated = testTemplateService.recordTestResult(testResultId, dto);
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error recording test result {}", testResultId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
        summary = "Generate QC Test Report",
        description = "Generates a comprehensive QC test report for a work order. Includes summary statistics, pass/fail count, and any failed tests.",
        tags = {"QC Testing"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Test report generated",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TestReportDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Work Order not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/test-report")
    public ResponseEntity<?> getTestReport(
        @Parameter(
            name = "id",
            description = "Work Order ID",
            required = true,
            example = "1"
        )
        @PathVariable int id) {
        try {
            TestReportDTO report = testTemplateService.generateTestReport(id);
            return ResponseEntity.ok(report);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating test report for WorkOrder {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
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

    @GetMapping("/{id}/export/job-sheet")
    public ResponseEntity<byte[]> exportJobSheet(@PathVariable Integer id) {
        try {
            byte[] pdf = workOrderExportService.generateWorkOrderJobSheet(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Work_Order_Job_Sheet.pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            logger.warn("Work order not found for job sheet export, id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            logger.error("Error generating work order job sheet for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @GetMapping("/{id}/export/operation-instruction-cards")
    public ResponseEntity<byte[]> exportOperationInstructionCards(@PathVariable Integer id) {
        try {
            byte[] pdf = workOrderExportService.generateOperationInstructionCards(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Operation_Cards_WO_" + id + ".pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            logger.warn("Work order not found for operation cards export, id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            logger.error("Error generating operation instruction cards for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}/export/material-pick-list")
    public ResponseEntity<byte[]> exportMaterialPickList(@PathVariable Integer id) {
        try {
            byte[] pdf = workOrderExportService.generateMaterialPickList(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Material_Pick_List_WO_" + id + ".pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            logger.warn("Work order not found for pick list export, id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            logger.error("Error generating material pick list for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}/export/move-tickets")
    public ResponseEntity<byte[]> exportMoveTickets(@PathVariable Integer id) {
        try {
            byte[] pdf = workOrderExportService.generateMoveTickets(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Move_Tickets_WO_" + id + ".pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            logger.warn("Work order not found for move tickets export, id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            logger.error("Error generating move tickets for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
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

