package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.spec.GenericSpecification;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import com.nextgenmanager.nextgenmanager.production.enums.*;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderListMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderMapper;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.model.TestTemplate;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperationDependency;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderTestResult;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.WorkCenterRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderTestResultRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderDto;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.service.SalesOrderService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class WorkOrderServiceImpl implements WorkOrderService{

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderMaterialRepository workOrderMaterialRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Autowired
    private WorkOrderAuditService auditService;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService inventoryInstanceService;

    @Autowired
    private BomService bomService;

    @Autowired
    private WorkCenterRepository workCenterRepository;

    @Autowired
    private TestTemplateService testTemplateService;

    @Autowired
    private WorkOrderTestResultRepository workOrderTestResultRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderService.class);

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderListMapper workOrderListMapper;



    @Override
    public WorkOrderDTO getWorkOrder(int id) {

        WorkOrder workOrder = workOrderRepository.getReferenceById(id);
        logger.debug("Fetched Work Order: {}", workOrder.getWorkOrderNumber());
        return workOrderMapper.toDTO(workOrder);
    }

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "bomName", "bom.bomName",
            "salesOrderNumber", "salesOrder.orderNumber",
            "workCenter", "workCenter.centerName",
            "parentWorkOrderNumber", "parentWorkOrder.workOrderNumber",
            "status", "workOrderStatus"
    );


    @Transactional
    @Override
    public WorkOrderDTO addWorkOrder(WorkOrderRequestDTO dto) {

        // Validate mandatory inputs
        logger.debug("Creating Work Order for BOM ID: {}, Planned Qty: {}",
                dto.getBomId(), dto.getPlannedQuantity());

        if (dto.getBomId() == null) {
            throw new IllegalArgumentException("BOM ID is required to create Work Order");
        }

        if (dto.getPlannedQuantity() == null || dto.getPlannedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Planned quantity must be greater than zero");
        }

        // ── Resolve entities from IDs ───────────────────────────────────────────
        Bom bom = bomService.getBom(dto.getBomId());
        if (bom == null) {
            throw new EntityNotFoundException("BOM not found with ID: " + dto.getBomId());
        }

        Routing routing = routingService.getRoutingEntityByBom(bom.getId());
        if (routing == null) {
            throw new EntityNotFoundException("No routing found for BOM ID: " + dto.getBomId());
        }

        List<BomPosition> bomPositions = bom.getPositions();
        List<RoutingOperation> routingOperations = routing.getOperations();

        if (bomPositions.isEmpty() && routingOperations.isEmpty()) {
            throw new IllegalStateException("BOM has no Material OR Operations for BOM ID: " + bom.getId());
        }



        SalesOrder salesOrder = null;
        if (dto.getSalesOrderId() != null) {
            salesOrder = salesOrderRepository.findById(dto.getSalesOrderId().longValue())
                    .orElseThrow(() -> new EntityNotFoundException("Sales Order not found with ID: " + dto.getSalesOrderId()));
        }

        WorkOrder parentWorkOrder = null;
        if (dto.getParentWorkOrderId() != null) {
            parentWorkOrder = workOrderRepository.findById(dto.getParentWorkOrderId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent WorkOrder not found with ID: " + dto.getParentWorkOrderId()));
        }

        WorkCenter workCenter = null;
        if (dto.getWorkCenterId() != null) {
            workCenter = workCenterRepository.findById(dto.getWorkCenterId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkCenter not found with ID: " + dto.getWorkCenterId()));
        }

        // ── Create Work Order header ────────────────────────────────────────────
        WorkOrder workOrder = new WorkOrder();
        workOrder.setWorkOrderNumber(generateWorkOrderNumber());
        workOrder.setSalesOrder(salesOrder);
        workOrder.setParentWorkOrder(parentWorkOrder);
        workOrder.setBom(bom);
        workOrder.setRouting(routing);
        workOrder.setWorkCenter(workCenter);

        workOrder.setPlannedQuantity(dto.getPlannedQuantity());
        workOrder.setCompletedQuantity(BigDecimal.ZERO);
        workOrder.setScrappedQuantity(BigDecimal.ZERO);

        workOrder.setPriority(dto.getPriority() != null ? dto.getPriority() : WorkOrderPriority.NORMAL);
        workOrder.setSourceType(dto.getSourceType());
        workOrder.setRemarks(dto.getRemarks());
        workOrder.setAllowBackflush(dto.isAllowBackflush());

        workOrder.setDueDate(dto.getDueDate());
        workOrder.setPlannedStartDate(dto.getPlannedStartDate());
        workOrder.setPlannedEndDate(dto.getPlannedEndDate());

        workOrder.setWorkOrderStatus(WorkOrderStatus.CREATED);

        // Save header first (ID needed)
        workOrder = workOrderRepository.save(workOrder);

        // ── Step 1: Explode Routing → WorkOrderOperation ──────────────────────────
        List<WorkOrderOperation> operations = new ArrayList<>();
        BigDecimal totalProductionMinutes = BigDecimal.ZERO;

        for (RoutingOperation routingOp : routing.getOperations()) {

            WorkOrderOperation woo = new WorkOrderOperation();
            woo.setWorkOrder(workOrder);
            woo.setRoutingOperation(routingOp);
            woo.setSequence(routingOp.getSequenceNumber());
            woo.setOperationName(routingOp.getName());
            woo.setWorkCenter(routingOp.getWorkCenter());

            woo.setPlannedQuantity(dto.getPlannedQuantity());
            woo.setCompletedQuantity(BigDecimal.ZERO);
            woo.setScrappedQuantity(BigDecimal.ZERO);

            woo.setStatus(OperationStatus.PLANNED);

            // Copy machine from routing for machine-level scheduling
            if (routingOp.getMachineDetails() != null) {
                woo.setAssignedMachine(routingOp.getMachineDetails());
            }

            operations.add(woo);

            // ── Calculate estimated time for this operation ──
            BigDecimal setupTime = routingOp.getSetupTime() != null ? routingOp.getSetupTime() : BigDecimal.ZERO;
            BigDecimal runTime = routingOp.getRunTime() != null ? routingOp.getRunTime() : BigDecimal.ZERO;
            BigDecimal opTime = setupTime.add(runTime.multiply(dto.getPlannedQuantity()));
            totalProductionMinutes = totalProductionMinutes.add(opTime);
        }

        // Set first operation's available input = planned qty (gate 2 initialization)
        operations.sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));
        WorkOrderOperation firstOperation = null;
        if (!operations.isEmpty()) {
            firstOperation = operations.get(0);
            firstOperation.setAvailableInputQuantity(dto.getPlannedQuantity());
            firstOperation.setStatus(OperationStatus.READY);
        }

        workOrderOperationRepository.saveAll(operations);

        // Set estimated production time
        workOrder.setEstimatedProductionMinutes(totalProductionMinutes.setScale(2, RoundingMode.HALF_UP));

        // Build routingOperationId → WorkOrderOperation for material linking
        Map<Long, WorkOrderOperation> routingOpToWoo = new HashMap<>();
        for (WorkOrderOperation woo : operations) {
            if (woo.getRoutingOperation() != null) {
                routingOpToWoo.put(woo.getRoutingOperation().getId(), woo);
            }
        }

        // ── Step 2: Explode BOM → WorkOrderMaterial ───────────────────────────────
        List<WorkOrderMaterial> materials = new ArrayList<>();

        for (BomPosition bomItem : bomPositions) {

            WorkOrderMaterial wom = new WorkOrderMaterial();
            wom.setWorkOrder(workOrder);
            wom.setComponent(bomItem.getChildInventoryItem());

            BigDecimal baseQty = BigDecimal.valueOf(bomItem.getQuantity());
            BigDecimal plannedQty = dto.getPlannedQuantity();

            BigDecimal scrapPercent = bomItem.getScrapPercentage() != null
                    ? bomItem.getScrapPercentage()
                    : BigDecimal.ZERO;

            // ---- NET REQUIRED (Actual Need) ----
            BigDecimal netRequired = baseQty
                    .multiply(plannedQty)
                    .setScale(5, RoundingMode.HALF_UP);

            // ---- SCRAP MULTIPLIER ----
            BigDecimal scrapMultiplier = BigDecimal.ONE.add(
                    scrapPercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
            );

            // ---- PLANNED REQUIRED (With Scrap Buffer) ----
            BigDecimal plannedRequired = netRequired
                    .multiply(scrapMultiplier)
                    .setScale(5, RoundingMode.HALF_UP);

            wom.setNetRequiredQuantity(netRequired);
            wom.setPlannedRequiredQuantity(plannedRequired);
            wom.setScrappercent(scrapPercent);

            wom.setIssuedQuantity(BigDecimal.ZERO);
            wom.setScrappedQuantity(BigDecimal.ZERO);
            wom.setIssueStatus(MaterialIssueStatus.NOT_ISSUED);

            // ---- Link to operation gate (snapshot from BOM position) ----
            // If routing operation is specified in BOM, use that; otherwise default to first operation
            if (bomItem.getRoutingOperation() != null) {
                WorkOrderOperation linkedOp = routingOpToWoo.get(
                        bomItem.getRoutingOperation().getId()
                );
                if (linkedOp != null) {
                    wom.setWorkOrderOperation(linkedOp);
                    logger.debug("Material {} linked to operation: {}",
                        wom.getComponent().getName(), bomItem.getRoutingOperation().getName());
                } else {
                    // BOM points to a routing op not in this work order — fall back to first
                    logger.warn("BOM routing op ID {} not found in work order ops for material {}, falling back to first operation",
                        bomItem.getRoutingOperation().getId(), wom.getComponent().getName());
                    wom.setWorkOrderOperation(firstOperation);
                }
            } else {
                // No routing operation specified — material required at first operation
                wom.setWorkOrderOperation(firstOperation);
                logger.debug("Material {} has no routing operation, assigned to first operation: {}",
                    wom.getComponent().getName(), firstOperation != null ? firstOperation.getOperationName() : "none");
            }

            materials.add(wom);
        }

        workOrderMaterialRepository.saveAll(materials);

        // ── Step 3: Copy TestTemplates → WorkOrderTestResult ─────────────────────
        List<WorkOrderTestResult> testResults = new ArrayList<>();
        if (bom.getParentInventoryItem() != null) {
            List<TestTemplate> templates = testTemplateService.getActiveTemplatesForItem(
                    bom.getParentInventoryItem().getInventoryItemId());
            for (TestTemplate tmpl : templates) {
                WorkOrderTestResult tr = new WorkOrderTestResult();
                tr.setWorkOrder(workOrder);
                tr.setTestTemplate(tmpl);
                // Frozen snapshot
                tr.setTestName(tmpl.getTestName());
                tr.setInspectionType(tmpl.getInspectionType());
                tr.setSampleSize(tmpl.getSampleSize());
                tr.setIsMandatory(tmpl.getIsMandatory());
                tr.setSequence(tmpl.getSequence());
                tr.setAcceptanceCriteria(tmpl.getAcceptanceCriteria());
                tr.setUnitOfMeasure(tmpl.getUnitOfMeasure());
                tr.setMinValue(tmpl.getMinValue());
                tr.setMaxValue(tmpl.getMaxValue());
                tr.setResult(com.nextgenmanager.nextgenmanager.production.enums.TestResult.PENDING);
                testResults.add(tr);
            }
            if (!testResults.isEmpty()) {
                workOrderTestResultRepository.saveAll(testResults);
                logger.info("Copied {} test templates into WorkOrder {}",
                        testResults.size(), workOrder.getWorkOrderNumber());
            }
        }

        // Attach children to header
        workOrder.setMaterials(materials);
        workOrder.setOperations(operations);
        workOrder.setTestResults(testResults);

        // Save updated estimation
        workOrder = workOrderRepository.save(workOrder);

        // Record audit
        auditService.record(
                workOrder,
                WorkOrderEventType.CREATED,
                null,
                null,
                null,
                "WorkOrder created"
        );

        logger.info("Created Work Order: {} with {} materials and {} operations",
                workOrder.getWorkOrderNumber(),
                materials.size(),
                operations.size());
        return workOrderMapper.toDTO(workOrder);
    }

    @Override
    public Page<WorkOrderListDTO> getAllWorkOrders(
           FilterRequest request
    ) {

        Sort.Direction direction = Sort.Direction.fromString(request.getSortDir()); // safer
        String sortBy = request.getSortBy();
        if (JOIN_FIELD_MAP.containsKey(sortBy)) {
            sortBy = JOIN_FIELD_MAP.get(sortBy);
        }
        Sort sort = Sort.by(direction, sortBy);


        List<FilterCriteria> filters = request.getFilters();
        FilterCriteria filterDeleteDateIsNull = new FilterCriteria("deletedDate", "=", null);
        filters.add(filterDeleteDateIsNull);
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Specification<WorkOrder> spec = GenericSpecification.buildSpecification(filters, JOIN_FIELD_MAP);


        Page<WorkOrder> workOrders = workOrderRepository.findAll(spec, pageable);

        logger.debug("Fetched {} Work Orders for page {}, size {}",
                workOrders.getNumberOfElements(), pageable.getPageNumber(), pageable.getPageSize());
        //  Map to DTO
        return workOrders.map(workOrderListMapper::toDTO);
    }


    private String generateWorkOrderNumber() {
        Long seq = workOrderRepository.getNextWorkOrderSequence();
        return "WO-" + seq;
    }

    private BigDecimal calculateWorkOrderCompletedQuantity(WorkOrder workOrder) {

        // ---- OPERATION BASED COMPLETION ----
        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrder(workOrder);

        BigDecimal operationCompletedUnits = operations.stream()
                .filter(op -> op.getDeletedDate() == null)
                .map(op -> op.getCompletedQuantity() != null
                        ? op.getCompletedQuantity()
                        : BigDecimal.ZERO)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);


        // ---- MATERIAL BASED COMPLETION ----
        List<WorkOrderMaterial> materials =
                workOrderMaterialRepository.findByWorkOrder(workOrder);

        Optional<BigDecimal> materialCompletedUnits = materials.stream()
                .filter(mat -> mat.getDeletedDate() == null)
                .map(mat -> {

                    if (mat.getNetRequiredQuantity() == null ||
                            mat.getNetRequiredQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        return null;
                    }

                    BigDecimal plannedQty = workOrder.getPlannedQuantity() != null
                            ? workOrder.getPlannedQuantity()
                            : BigDecimal.ZERO;

                    if (plannedQty.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }

                    BigDecimal issued = mat.getIssuedQuantity() != null
                            ? mat.getIssuedQuantity()
                            : BigDecimal.ZERO;

                    BigDecimal scrapped = mat.getScrappedQuantity() != null
                            ? mat.getScrappedQuantity()
                            : BigDecimal.ZERO;

                    // ✅ GOOD CONSUMED
                    BigDecimal goodConsumed = issued.subtract(scrapped);

                    if (goodConsumed.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }

                    // Completion ratio
                    BigDecimal completionRatio = goodConsumed
                            .divide(mat.getNetRequiredQuantity(), 10, RoundingMode.DOWN);

                    // Units completed
                    BigDecimal completedByMaterial = completionRatio
                            .multiply(plannedQty)
                            .setScale(0, RoundingMode.DOWN);

                    return completedByMaterial.max(BigDecimal.ZERO);
                })
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo);


        // ---- FINAL COMPLETION ----
        return materialCompletedUnits
                .map(operationCompletedUnits::min)
                .orElse(operationCompletedUnits);
    }


    private static LocalDate toLocalDate(Date date) {
        return date == null
                ? null
                : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Override
    public WorkOrderSummaryDTO getWorkOrderSummary() {
        return getWorkOrderSummary(LocalDate.now());
    }

    WorkOrderSummaryDTO getWorkOrderSummary(LocalDate today) {
        Specification<WorkOrder> spec = (root, query, cb) -> cb.isNull(root.get("deletedDate"));
        List<WorkOrder> workOrders = workOrderRepository.findAll(spec);

        long overdue = 0;
        long dueSoon = 0;
        long ready = 0;
        long inProgress = 0;
        long completedToday = 0;
        long blocked = 0;

        LocalDate dueSoonEnd = today.plusDays(3);

        for (WorkOrder workOrder : workOrders) {
            WorkOrderStatus status = workOrder.getWorkOrderStatus();
            boolean activeStatus = status != WorkOrderStatus.COMPLETED
                    && status != WorkOrderStatus.CLOSED
                    && status != WorkOrderStatus.CANCELLED
                    && status != WorkOrderStatus.SHORT_CLOSED;

            LocalDate dueDate = toLocalDate(workOrder.getDueDate());
            if (dueDate != null && dueDate.isBefore(today) && activeStatus) {
                overdue++;
            }
            if (dueDate != null
                    && (dueDate.isEqual(today) || dueDate.isAfter(today))
                    && (dueDate.isEqual(dueSoonEnd) || dueDate.isBefore(dueSoonEnd))
                    && activeStatus) {
                dueSoon++;
            }

            if (status == WorkOrderStatus.IN_PROGRESS) {
                inProgress++;
            }
            if (status == WorkOrderStatus.HOLD) {
                blocked++;
            }

            LocalDate plannedEndDate = toLocalDate(workOrder.getPlannedEndDate());
            if (plannedEndDate != null && plannedEndDate.isEqual(today)) {
                completedToday++;
            }

            if (status == WorkOrderStatus.READY_FOR_INSPECTION
                   ) {
                ready++;
            }
        }

        return new com.nextgenmanager.nextgenmanager.production.dto.WorkOrderSummaryDTO(
                overdue,
                dueSoon,
                ready,
                inProgress,
                completedToday,
                blocked
        );
    }


    @Transactional
    @Override
    public WorkOrderDTO  updateWorkOrder(int workOrderId, WorkOrderRequestDTO dto) {

        logger.debug("Updating WorkOrder id={}", workOrderId);

        // Fetch existing Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard — allow update in CREATED or SCHEDULED
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
            logger.warn(
                    "Update rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "WorkOrder can only be updated in CREATED or SCHEDULED status"
            );
        }

        // Forbidden changes
        if (dto.getBomId() != null && dto.getBomId() != workOrder.getBom().getId()) {
            logger.error("Attempt to change BOM for WorkOrder {}", workOrder.getWorkOrderNumber());
            throw new IllegalStateException("BOM cannot be changed after WorkOrder creation");
        }

        if (dto.getRoutingId() != null &&
                !dto.getRoutingId().equals(workOrder.getRouting().getId())) {
            logger.error("Attempt to change Routing for WorkOrder {}", workOrder.getWorkOrderNumber());
            throw new IllegalStateException("Routing cannot be changed after WorkOrder creation");
        }

        if (dto.getWorkCenterId() != null) {
            WorkCenter workCenter = workCenterRepository.findById(dto.getWorkCenterId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkCenter not found with ID: " + dto.getWorkCenterId()));
            workOrder.setWorkCenter(workCenter);
        }

        // Update allowed fields
        workOrder.setRemarks(dto.getRemarks());
        workOrder.setAllowBackflush(dto.isAllowBackflush());
        workOrder.setDueDate(dto.getDueDate());
        workOrder.setPlannedStartDate(dto.getPlannedStartDate());
        workOrder.setPlannedEndDate(dto.getPlannedEndDate());
        workOrder.setActualStartDate(dto.getActualStartDate());
        workOrder.setActualEndDate(dto.getActualEndDate());

        // Update priority
        if (dto.getPriority() != null) {
            workOrder.setPriority(dto.getPriority());
        }

        // Capture old quantity for audit trail
        BigDecimal oldQty = workOrder.getPlannedQuantity();
        BigDecimal newQty = oldQty;

        BigDecimal oldScrappedQty = workOrder.getScrappedQuantity();
        BigDecimal newScrappedQty = oldScrappedQty;
        if (dto.getPlannedQuantity() != null &&
                dto.getPlannedQuantity().compareTo(BigDecimal.ZERO) > 0) {

            // ERP Rule: Prevent quantity changes if operations/materials already in progress
            List<WorkOrderOperation> operations =
                    workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

            if (!operations.isEmpty() && workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                    && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
                logger.error(
                        "Quantity change rejected: operations already exist for WorkOrder {}",
                        workOrder.getWorkOrderNumber()
                );
                throw new IllegalStateException(
                        "Planned quantity cannot be changed after operations are created"
                );
            }

            // Check if any materials have been issued
            List<WorkOrderMaterial> materials =
                    workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

            boolean anyMaterialIssued = materials.stream()
                    .anyMatch(m -> m.getIssueStatus() != MaterialIssueStatus.NOT_ISSUED);

            if (anyMaterialIssued) {
                logger.error(
                        "Quantity change rejected: materials already issued for WorkOrder {}",
                        workOrder.getWorkOrderNumber()
                );
                throw new IllegalStateException(
                        "Planned quantity cannot be changed when materials have been issued"
                );
            }

            logger.debug(
                    "Updating planned quantity for WorkOrder {} from {} to {}",
                    workOrder.getWorkOrderNumber(),
                    oldQty,
                    dto.getPlannedQuantity()
            );

            // Calculate ratio
            BigDecimal quantityRatio = dto.getPlannedQuantity().divide(
                    oldQty,
                    10,
                    RoundingMode.HALF_UP
            );

            // Update all material quantities proportionally
            for (WorkOrderMaterial material : materials) {

                BigDecimal oldNetQty = material.getNetRequiredQuantity();
                BigDecimal oldPlannedQty = material.getPlannedRequiredQuantity();

                BigDecimal newNetQty = oldNetQty
                        .multiply(quantityRatio)
                        .setScale(5, RoundingMode.HALF_UP);

                BigDecimal newPlannedQty = oldPlannedQty
                        .multiply(quantityRatio)
                        .setScale(5, RoundingMode.HALF_UP);

                logger.debug(
                        "Recalculating material {} | Net: {} -> {} | Planned: {} -> {} | WO {}",
                        material.getComponent().getItemCode(),
                        oldNetQty,
                        newNetQty,
                        oldPlannedQty,
                        newPlannedQty,
                        workOrder.getWorkOrderNumber()
                );

                material.setNetRequiredQuantity(newNetQty);
                material.setPlannedRequiredQuantity(newPlannedQty);
            }

            workOrderMaterialRepository.saveAll(materials);

            // Update all operation quantities proportionally
            for (WorkOrderOperation operation : operations) {
                BigDecimal newOperationQty = operation.getPlannedQuantity()
                        .multiply(quantityRatio)
                        .setScale(5, RoundingMode.HALF_UP);

                logger.debug(
                        "Recalculating operation {} quantity from {} to {} for WorkOrder {}",
                        operation.getSequence(),
                        operation.getPlannedQuantity(),
                        newOperationQty,
                        workOrder.getWorkOrderNumber()
                );

                operation.setPlannedQuantity(newOperationQty);
            }

            workOrderOperationRepository.saveAll(operations);

            newQty = dto.getPlannedQuantity();
            workOrder.setPlannedQuantity(newQty);

            newScrappedQty = dto.getScrappedQuantity();
            workOrder.setScrappedQuantity(newScrappedQty);
        }

        // ── Source type handling (ID-based) ─────────────────────────────────────
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.CREATED
                || workOrder.getWorkOrderStatus() == WorkOrderStatus.SCHEDULED) {
            workOrder.setSourceType(dto.getSourceType());
            if (dto.getSourceType() != null) {

                if (dto.getSourceType() == WorkOrderSourceType.SALES_ORDER) {
                    if (dto.getSalesOrderId() == null) {
                        throw new IllegalStateException(
                                "Cannot set source type to SALES_ORDER when no Sales Order ID is provided"
                        );
                    }
                    SalesOrder salesOrder = salesOrderRepository.findById(dto.getSalesOrderId().longValue())
                            .orElseThrow(() -> new EntityNotFoundException("SalesOrder not found"));
                    workOrder.setSalesOrder(salesOrder);
                    workOrder.setParentWorkOrder(null);
                } else if (dto.getSourceType() == WorkOrderSourceType.PARENT_WORK_ORDER) {
                    if (dto.getParentWorkOrderId() == null) {
                        throw new IllegalStateException(
                                "Cannot set source type to PARENT_WORK_ORDER when no Parent Work Order ID is provided"
                        );
                    }
                    WorkOrder parentWO = workOrderRepository.findById(dto.getParentWorkOrderId())
                            .orElseThrow(() -> new EntityNotFoundException("Parent WorkOrder not found"));
                    workOrder.setParentWorkOrder(parentWO);
                    workOrder.setSalesOrder(null);
                } else {
                    workOrder.setSalesOrder(null);
                    workOrder.setParentWorkOrder(null);
                }
            }
        }

        //  Persist
        WorkOrder updated = workOrderRepository.save(workOrder);

        // Record audit with correct old and new values
        if (!Objects.equals(oldQty, newQty)) {
            auditService.record(
                    workOrder,
                    WorkOrderEventType.UPDATED,
                    "plannedQuantity",
                    oldQty.toString(),
                    newQty.toString(),
                    "Planned quantity updated from " + oldQty + " to " + newQty
            );
        }
        if (!Objects.equals(oldScrappedQty, newScrappedQty)) {
            auditService.record(
                    workOrder,
                    WorkOrderEventType.UPDATED,
                    "scrappedQuantity",
                    oldScrappedQty.toString(),
                    newScrappedQty.toString(),
                    "Scrapped quantity updated from " + oldScrappedQty + " to " + newScrappedQty
            );
        }

        logger.info(
                "WorkOrder {} updated successfully",
                updated.getWorkOrderNumber()
        );

        return workOrderMapper.toDTO(updated);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parallel operation helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves operation dependencies from the routing and sets the initial status
     * of each WorkOrderOperation when a work order is released.
     *
     * <p>New mode (routing has explicit RoutingOperationDependency entries):
     * Each operation's dependsOnOperationIds is populated from the routing.
     * Operations with no dependencies → READY.
     * Operations with unresolved dependencies → WAITING_FOR_DEPENDENCY.
     *
     * <p>Legacy mode (no explicit dependencies defined in routing):
     * Falls back to sequential ordering — each operation depends on the previous
     * one by sequence number. Only the first operation is set to READY.
     *
     * @param operations list ordered by sequence (ascending)
     * @param plannedQty the work order's planned quantity, set as availableInputQuantity for READY ops
     */
    private void initializeOperationDependencies(List<WorkOrderOperation> operations,
                                                  BigDecimal plannedQty) {
        // Check if any routing operation has explicit dependencies defined
        boolean hasExplicitDependencies = operations.stream()
                .anyMatch(op -> op.getRoutingOperation() != null
                        && !op.getRoutingOperation().getDependencies().isEmpty());

        if (hasExplicitDependencies) {
            // Build a lookup: routingOperationId → WorkOrderOperation
            Map<Long, WorkOrderOperation> byRoutingOpId = new HashMap<>();
            for (WorkOrderOperation op : operations) {
                if (op.getRoutingOperation() != null) {
                    byRoutingOpId.put(op.getRoutingOperation().getId(), op);
                }
            }

            // Resolve routing-level dependencies into WO-level operation IDs
            for (WorkOrderOperation op : operations) {
                if (op.getRoutingOperation() == null) continue;

                Set<Long> depIds = new HashSet<>();
                for (com.nextgenmanager.nextgenmanager.production.model.RoutingOperationDependency rod
                        : op.getRoutingOperation().getDependencies()) {

                    if (rod.getDependencyType() == DependencyType.SEQUENTIAL) {
                        WorkOrderOperation depWoOp =
                                byRoutingOpId.get(rod.getDependsOnRoutingOperation().getId());
                        if (depWoOp != null) {
                            depIds.add(depWoOp.getId());
                        }
                    }
                }
                op.setDependsOnOperationIds(depIds);
                op.setParallelPath(op.getRoutingOperation().getParallelPath());
            }
        } else {
            // Legacy mode: each op depends on the previous by sequence
            WorkOrderOperation prev = null;
            for (WorkOrderOperation op : operations) {
                if (prev != null) {
                    op.setDependsOnOperationIds(new HashSet<>(Set.of(prev.getId())));
                }
                prev = op;
            }
        }

        // Set initial statuses based on resolved dependencies
        for (WorkOrderOperation op : operations) {
            if (op.getDependsOnOperationIds().isEmpty()) {
                op.setStatus(OperationStatus.READY);
                op.setAvailableInputQuantity(plannedQty);
                logger.info("Operation [{} - {}] set to READY (no dependencies)",
                        op.getSequence(), op.getOperationName());
            } else {
                op.setStatus(OperationStatus.WAITING_FOR_DEPENDENCY);
                logger.info("Operation [{} - {}] set to WAITING_FOR_DEPENDENCY ({} deps)",
                        op.getSequence(), op.getOperationName(), op.getDependsOnOperationIds().size());
            }
        }

        workOrderOperationRepository.saveAll(operations);
    }

    /**
     * After an operation is marked COMPLETED, finds all operations that depend on it
     * and unlocks any that now have all their dependencies satisfied.
     *
     * <p>New mode: uses the dependsOnOperationIds set (populated at release time).
     * Unlocked operations get status READY and their availableInputQuantity set to
     * the work order's planned quantity.
     *
     * <p>Legacy mode (no dependents found via dependsOnOperationIds):
     * Falls back to unlocking the next-by-sequence operation.
     *
     * @param completed  the operation that just became COMPLETED
     * @param completedQty  the quantity completed (used for legacy sequential forwarding)
     * @param workOrder  parent work order (used for legacy lookup)
     */
    private void unlockEligibleDependents(WorkOrderOperation completed,
                                           BigDecimal completedQty,
                                           WorkOrder workOrder) {
        List<WorkOrderOperation> dependents =
                workOrderOperationRepository.findByDependsOnOperationId(completed.getId());

        if (!dependents.isEmpty()) {
            // New dependency mode
            for (WorkOrderOperation dep : dependents) {
                List<WorkOrderOperation> allDeps =
                        workOrderOperationRepository.findAllById(dep.getDependsOnOperationIds());

                boolean allComplete = allDeps.stream()
                        .allMatch(d -> d.getStatus() == OperationStatus.COMPLETED);

                if (allComplete) {
                    dep.setStatus(OperationStatus.READY);
                    dep.setAvailableInputQuantity(workOrder.getPlannedQuantity());
                    dep.setDependencyResolvedDate(new Date());
                    workOrderOperationRepository.save(dep);

                    logger.info("Unlocked operation [{} - {}] for WorkOrder {} — all dependencies complete",
                            dep.getSequence(), dep.getOperationName(), workOrder.getWorkOrderNumber());
                }
            }
        } else {
            // Legacy mode: unlock next-by-sequence
            WorkOrderOperation nextOp =
                    workOrderOperationRepository
                            .findTopByWorkOrderAndSequenceGreaterThanOrderBySequenceAsc(
                                    workOrder, completed.getSequence()
                            );

            if (nextOp != null) {
                nextOp.setAvailableInputQuantity(nextOp.getAvailableInputQuantity().add(completedQty));
                nextOp.setStatus(OperationStatus.READY);
                workOrderOperationRepository.save(nextOp);

                logger.info("Next operation [{} - {}] set to READY with availableInput={} for WorkOrder {}",
                        nextOp.getSequence(), nextOp.getOperationName(),
                        nextOp.getAvailableInputQuantity(), workOrder.getWorkOrderNumber());
            }
        }
    }

    @Transactional
    @Override
    public WorkOrderDTO releaseWorkOrder(int workOrderId, boolean forceRelease) {

        logger.debug("Releasing WorkOrder id={}", workOrderId);

        //  Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById( workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard — allow release from CREATED or SCHEDULED
        String previousStatus = workOrder.getWorkOrderStatus().name();
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
            logger.warn(
                    "Release rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only WorkOrders in CREATED or SCHEDULED status can be released"
            );
        }

        //  Validate BOM & Routing
        if (workOrder.getBom() == null || workOrder.getRouting() == null) {
            logger.error(
                    "Release failed for WorkOrder {} due to missing BOM/Routing",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException("BOM and Routing are mandatory to release WorkOrder");
        }

        //  Validate materials
        List<WorkOrderMaterial> materials =
                workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

        if (materials.isEmpty() && operations.isEmpty()) {
            logger.error(
                    "Release failed for WorkOrder {}: Both materials and operations are missing",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException("WorkOrder must have materials or operations before release");
        }

        // Check for material shortages if not forceRelease
        if (!forceRelease) {
            List<String> shortages = new ArrayList<>();
            for (WorkOrderMaterial material : materials) {
                com.nextgenmanager.nextgenmanager.items.model.InventoryItem item = material.getComponent();
                double available = item.getProductInventorySettings() != null ? item.getProductInventorySettings().getAvailableQuantity() : 0.0;
                double required = material.getPlannedRequiredQuantity().doubleValue();
                if (available < required) {
                    shortages.add(item.getItemCode() + " (Req: " + required + ", Avail: " + available + ")");
                }
            }
            if (!shortages.isEmpty()) {
                throw new IllegalStateException(
                        "Insufficient inventory for materials: " + String.join(", ", shortages) +
                        ". Proceed manually by ignoring shortages (force release) or procure items."
                );
            }
        }

        // Allocate materials
        for (WorkOrderMaterial material : materials) {
            if (material.getPlannedRequiredQuantity().compareTo(BigDecimal.ZERO) > 0) {
                com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest req = inventoryInstanceService.requestInstance(
                        material.getComponent(),
                        material.getPlannedRequiredQuantity().doubleValue(),
                        com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource.WORK_ORDER,
                        (long) workOrder.getId(),
                        "System",
                        "Work Order Release Allocation"
                );
                material.setInventoryRequestId(req.getId());
                workOrderMaterialRepository.save(material);
            }
        }

        //  Update Work Order status
        workOrder.setWorkOrderStatus(WorkOrderStatus.RELEASED);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} released successfully",
                workOrder.getWorkOrderNumber()
        );

        //  Initialize operation statuses with dependency awareness
        if (!operations.isEmpty()) {
            initializeOperationDependencies(operations, workOrder.getPlannedQuantity());
        }
        auditService.record(
                workOrder,
                WorkOrderEventType.RELEASED,
                "status",
                previousStatus,
                "RELEASED",
                "WorkOrder released"
        );

        //  Return updated DTO
        return workOrderMapper.toDTO(workOrder);
    }

    @Transactional
    @Override
    public void startOperation(Long operationId) {

        logger.info("Starting WorkOrderOperation id={}", operationId);

        // Fetch operation
        WorkOrderOperation operation =
                workOrderOperationRepository.findById(operationId)
                        .orElseThrow(() -> {
                            logger.error("Operation not found id={}", operationId);
                            return new EntityNotFoundException("Operation not found");
                        });

        WorkOrder workOrder = operation.getWorkOrder();

        //  Validate WorkOrder status
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.RELEASED &&
                workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS) {

            logger.warn(
                    "Cannot start operation {} for WorkOrder {} due to WO status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );

            throw new IllegalStateException(
                    "Operation can only be started when WorkOrder is RELEASED or IN_PROGRESS"
            );
        }

        // Validate operation status
        if (operation.getStatus() != OperationStatus.READY) {
            logger.warn(
                    "Cannot start operation {} for WorkOrder {} due to operation status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    operation.getStatus()
            );
            throw new IllegalStateException("Only READY operations can be started");
        }

        //  Validate dependencies before starting
        //  New mode: check explicit dependsOnOperationIds (populated at release time).
        //  Legacy mode (empty set): fall back to previous-by-sequence check.
        Set<Long> dependsOnIds = operation.getDependsOnOperationIds();
        if (!dependsOnIds.isEmpty()) {
            // Parallel/dependency mode — check all declared dependencies are COMPLETED
            List<WorkOrderOperation> blockingOps =
                    workOrderOperationRepository.findAllById(dependsOnIds);

            List<String> pending = blockingOps.stream()
                    .filter(dep -> dep.getStatus() != OperationStatus.COMPLETED)
                    .map(dep -> "Op " + dep.getSequence() + " (" + dep.getOperationName() + ")")
                    .toList();

            if (!pending.isEmpty()) {
                logger.warn(
                        "Cannot start operation {} for WorkOrder {} — dependencies not complete: {}",
                        operation.getSequence(),
                        workOrder.getWorkOrderNumber(),
                        pending
                );
                throw new IllegalStateException(
                        "Cannot start: the following operations must complete first: " +
                        String.join(", ", pending)
                );
            }
        } else {
            // Legacy mode — enforce previous-by-sequence must be COMPLETED
            WorkOrderOperation previousOperation =
                    workOrderOperationRepository
                            .findTopByWorkOrderAndSequenceLessThanOrderBySequenceDesc(
                                    workOrder, operation.getSequence()
                            );

            if (previousOperation != null &&
                    previousOperation.getStatus() != OperationStatus.COMPLETED) {

                logger.warn(
                        "Cannot start operation {} before completing previous operation {} for WorkOrder {}",
                        operation.getSequence(),
                        previousOperation.getSequence(),
                        workOrder.getWorkOrderNumber()
                );

                throw new IllegalStateException(
                        "Previous operation must be COMPLETED before starting this operation"
                );
            }
        }

        // Material gate: materials with no operation assigned must be issued before any operation starts
        List<WorkOrderMaterial> unissuedWoLevelMaterials =
                workOrderMaterialRepository.findByWorkOrderAndWorkOrderOperationIsNullAndIssueStatusNot(
                        workOrder, MaterialIssueStatus.ISSUED
                );

        if (!unissuedWoLevelMaterials.isEmpty()) {
            String missing = unissuedWoLevelMaterials.stream()
                    .map(m -> m.getComponent().getItemCode())
                    .collect(java.util.stream.Collectors.joining(", "));

            logger.warn(
                    "Cannot start operation {} for WorkOrder {} — unissued WO-level materials: {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    missing
            );

            throw new IllegalStateException(
                    "The following materials must be fully issued before any operation can start " +
                    "(no operation assigned): " + missing
            );
        }

        //  Start operation
        operation.setStatus(OperationStatus.IN_PROGRESS);
        operation.setActualStartDate(new Date());
        workOrderOperationRepository.save(operation);

        logger.info(
                "Operation [{} - {}] started for WorkOrder {}",
                operation.getSequence(),
                operation.getOperationName(),
                workOrder.getWorkOrderNumber()
        );

        // Update WorkOrder status if first operation
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.RELEASED
                || workOrder.getWorkOrderStatus() == WorkOrderStatus.SCHEDULED) {
            workOrder.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
            workOrder.setActualStartDate(new Date());
            workOrderRepository.save(workOrder);

            logger.info(
                    "WorkOrder {} moved to IN_PROGRESS",
                    workOrder.getWorkOrderNumber()
            );
        }

        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_STARTED,
                "operation",
                null,
                operation.getSequence() + " - " + operation.getOperationName(),
                "Operation started"
        );
    }

    @Transactional
    @Override
    public void issueMaterials(IssueWorkOrderMaterialDTO issueDTO) {

        logger.info("Issuing materials for WorkOrder id={}", issueDTO.getWorkOrderId());

        // Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(issueDTO.getWorkOrderId())
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", issueDTO.getWorkOrderId());
                    return new EntityNotFoundException("WorkOrder not found");
                });

        // Validate WorkOrder status
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.RELEASED &&
                workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS) {

            throw new IllegalStateException(
                    "Materials can only be issued when WorkOrder is RELEASED or IN_PROGRESS"
            );
        }

        for (IssueWorkOrderMaterialDTO.MaterialIssueItem item : issueDTO.getMaterials()) {

            WorkOrderMaterial material = workOrderMaterialRepository
                    .findById(item.getWorkOrderMaterialId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkOrderMaterial not found"));

            // Validate material belongs to work order
            if (!(material.getWorkOrder().getId() ==workOrder.getId())) {
                throw new IllegalStateException("Material does not belong to this WorkOrder");
            }

            // ----- OPERATION GATE -----
            // If this material is linked to a specific operation, that operation
            // must be READY or IN_PROGRESS before issue is allowed.
            if (material.getWorkOrderOperation() != null) {
                OperationStatus opStatus = material.getWorkOrderOperation().getStatus();
                if (opStatus != OperationStatus.READY && opStatus != OperationStatus.IN_PROGRESS) {
                    throw new IllegalStateException(
                            "Material '" + material.getComponent().getItemCode() +
                            "' can only be issued when operation '" +
                            material.getWorkOrderOperation().getOperationName() +
                            "' is READY or IN_PROGRESS (current: " + opStatus + ")"
                    );
                }
            }

            // ----- SAFE VALUE EXTRACTION -----

            BigDecimal currentIssued = material.getIssuedQuantity() != null
                    ? material.getIssuedQuantity()
                    : BigDecimal.ZERO;

            BigDecimal currentScrapped = material.getScrappedQuantity() != null
                    ? material.getScrappedQuantity()
                    : BigDecimal.ZERO;

            BigDecimal newIssued = item.getIssuedQuantity() != null
                    ? item.getIssuedQuantity()
                    : BigDecimal.ZERO;

            BigDecimal newScrap = item.getScrappedQuantity() != null
                    ? item.getScrappedQuantity()
                    : BigDecimal.ZERO;

            if (newIssued.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Issued quantity must be greater than zero");
            }

            if (newScrap.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Scrap quantity cannot be negative");
            }

            // ----- CALCULATIONS -----

            BigDecimal totalIssued = currentIssued.add(newIssued);
            BigDecimal totalScrapped = currentScrapped.add(newScrap);

            // Prevent over-issue beyond planned quantity
            if (totalIssued.compareTo(material.getPlannedRequiredQuantity()) > 0) {
                throw new IllegalStateException(
                        "Issued quantity exceeds planned required quantity"
                );
            }

            // Scrap cannot exceed total issued
            if (totalScrapped.compareTo(totalIssued) > 0) {
                throw new IllegalStateException(
                        "Scrap quantity cannot exceed issued quantity"
                );
            }

            // Update quantities
            material.setIssuedQuantity(totalIssued);
            material.setScrappedQuantity(totalScrapped);

            // Consume inventory
            if (material.getInventoryRequestId() != null) {
                try {
                    inventoryInstanceService.consumeInventoryInstance(material.getComponent(), newIssued.doubleValue(), material.getInventoryRequestId());
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to consume inventory: " + e.getMessage(), e);
                }
            } else {
                logger.warn("Material {} does not have an inventory request ID linked. Skipping inventory consumption.", material.getComponent().getItemCode());
            }

            // ----- STATUS BASED ON NET REQUIREMENT -----

            BigDecimal goodConsumed = totalIssued.subtract(totalScrapped);

            if (goodConsumed.compareTo(material.getNetRequiredQuantity()) >= 0) {
                material.setIssueStatus(MaterialIssueStatus.ISSUED);

                logger.info(
                        "Material {} fully satisfied for WorkOrder {}",
                        material.getComponent().getItemCode(),
                        workOrder.getWorkOrderNumber()
                );

            } else if (totalIssued.compareTo(BigDecimal.ZERO) > 0) {
                material.setIssueStatus(MaterialIssueStatus.PARTIAL_ISSUED);

                logger.info(
                        "Material {} partially issued (Good: {}/{}) for WorkOrder {}",
                        material.getComponent().getItemCode(),
                        goodConsumed,
                        material.getNetRequiredQuantity(),
                        workOrder.getWorkOrderNumber()
                );

            } else {
                material.setIssueStatus(MaterialIssueStatus.NOT_ISSUED);
            }

            workOrderMaterialRepository.save(material);

            // Audit
            auditService.record(
                    workOrder,
                    WorkOrderEventType.UPDATED,
                    "materialIssue",
                    material.getComponent().getItemCode(),
                    totalIssued.toString(),
                    "Issued: " + newIssued + ", Scrap: " + newScrap
            );
        }

        logger.info(
                "Materials issued successfully for WorkOrder {}",
                workOrder.getWorkOrderNumber()
        );

        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);
        workOrder.setCompletedQuantity(totalCompleted);

        workOrderRepository.save(workOrder);
    }


    @Transactional
    @Override
    public void completeOperationPartial(PartialOperationCompleteDTO partialCompleteDTO) {

        logger.info("Completing operation id={} with partial qty={}",
                partialCompleteDTO.getOperationId(),
                partialCompleteDTO.getCompletedQuantity());

        // Fetch operation
        WorkOrderOperation operation = workOrderOperationRepository
                .findById(partialCompleteDTO.getOperationId())
                .orElseThrow(() -> {
                    logger.error("Operation not found id={}", partialCompleteDTO.getOperationId());
                    return new EntityNotFoundException("Operation not found");
                });

        WorkOrder workOrder = operation.getWorkOrder();

        // Validate operation status
        if (operation.getStatus() != OperationStatus.IN_PROGRESS &&
                operation.getStatus() != OperationStatus.READY) {
            throw new IllegalStateException(
                    "Only READY or IN_PROGRESS operations can be completed"
            );
        }

        // Validate quantity > 0
        BigDecimal batchQty = partialCompleteDTO.getCompletedQuantity();
        if (batchQty == null || batchQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Completed quantity must be greater than zero");
        }

        BigDecimal currentCompleted = operation.getCompletedQuantity();
        BigDecimal newCompleted = currentCompleted.add(batchQty);

        // ─── GATE 1: Input Qty Gate ─────────────────────────────────────────────
        // Can't complete more than what's been forwarded from the previous operation
        if (newCompleted.compareTo(operation.getAvailableInputQuantity()) > 0) {
            BigDecimal remaining = operation.getAvailableInputQuantity().subtract(currentCompleted);
            throw new IllegalStateException(
                    "Input gate: Only " + remaining + " units available from previous operation. " +
                    "Available input: " + operation.getAvailableInputQuantity() +
                    ", already completed: " + currentCompleted
            );
        }

        // Validate against planned quantity (if over-completion not allowed)
        if (!Boolean.TRUE.equals(operation.getAllowOverCompletion()) &&
                newCompleted.compareTo(operation.getPlannedQuantity()) > 0) {
            throw new IllegalStateException(
                    "Completed quantity (" + newCompleted +
                    ") exceeds planned quantity (" + operation.getPlannedQuantity() + ")"
            );
        }

        // ─── GATE 2: Material Gate ──────────────────────────────────────────────
        // Collect materials to consume: operation-specific + WO-level (null operation) for first op
        List<WorkOrderMaterial> opMaterials =
                workOrderMaterialRepository.findByWorkOrderOperationId(operation.getId());

        // WO-level materials (no operation assigned) are consumed at the first operation
        boolean isFirstOperation = workOrderOperationRepository
                .findTopByWorkOrderAndSequenceLessThanOrderBySequenceDesc(
                        workOrder, operation.getSequence()) == null;

        if (isFirstOperation) {
            List<WorkOrderMaterial> woLevelMaterials =
                    workOrderMaterialRepository.findByWorkOrderAndWorkOrderOperationIsNullAndIssueStatusNot(
                            workOrder, MaterialIssueStatus.NOT_ISSUED);

            // Also check that ALL WO-level materials (including NOT_ISSUED) are fully issued
            List<WorkOrderMaterial> unissuedWoLevelMaterials =
                    workOrderMaterialRepository.findByWorkOrderAndWorkOrderOperationIsNullAndIssueStatusNot(
                            workOrder, MaterialIssueStatus.ISSUED);

            if (!unissuedWoLevelMaterials.isEmpty()) {
                String missing = unissuedWoLevelMaterials.stream()
                        .map(m -> m.getComponent().getItemCode())
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new IllegalStateException(
                        "The following materials must be fully issued before completing this operation " +
                        "(no operation assigned — required from start): " + missing);
            }

            // Add WO-level materials for consumption
            opMaterials = new ArrayList<>(opMaterials);
            opMaterials.addAll(woLevelMaterials);
        }

        for (WorkOrderMaterial material : opMaterials) {
            // Calculate how much material is needed per unit of finished product
            BigDecimal requiredPerUnit = material.getNetRequiredQuantity()
                    .divide(workOrder.getPlannedQuantity(), 10, RoundingMode.HALF_UP);

            BigDecimal consumeQty = batchQty.multiply(requiredPerUnit)
                    .setScale(5, RoundingMode.HALF_UP);

            BigDecimal availableToConsume = material.getIssuedQuantity()
                    .subtract(material.getConsumedQuantity());

            if (availableToConsume.compareTo(consumeQty) < 0) {
                throw new IllegalStateException(
                        "Insufficient material '" + material.getComponent().getItemCode() +
                        "': need " + consumeQty +
                        ", available " + availableToConsume +
                        " (issued " + material.getIssuedQuantity() +
                        " - consumed " + material.getConsumedQuantity() + ")"
                );
            }

            // Consume material
            material.setConsumedQuantity(material.getConsumedQuantity().add(consumeQty));
            workOrderMaterialRepository.save(material);

            logger.info("Consumed {} of '{}' for operation {} (total consumed: {})",
                    consumeQty,
                    material.getComponent().getItemCode(),
                    operation.getOperationName(),
                    material.getConsumedQuantity());
        }

        // ─── Update Operation ───────────────────────────────────────────────────

        // Add scrapped quantity if provided
        if (partialCompleteDTO.getScrappedQuantity() != null &&
                partialCompleteDTO.getScrappedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            operation.setScrappedQuantity(
                    operation.getScrappedQuantity().add(partialCompleteDTO.getScrappedQuantity())
            );
        }

        operation.setCompletedQuantity(newCompleted);
        operation.setStatus(OperationStatus.IN_PROGRESS);

        if (operation.getActualStartDate() == null) {
            operation.setActualStartDate(new Date());
        }

        // ─── Mark as COMPLETED or forward partial qty ───────────────────────────
        if (newCompleted.compareTo(operation.getPlannedQuantity()) >= 0) {
            // Operation fully done — unlock downstream operations
            operation.setStatus(OperationStatus.COMPLETED);
            operation.setActualEndDate(new Date());
            unlockEligibleDependents(operation, newCompleted, workOrder);

            logger.info("Operation [{} - {}] fully COMPLETED for WorkOrder {}",
                    operation.getSequence(),
                    operation.getOperationName(),
                    workOrder.getWorkOrderNumber());
        } else {
            logger.info("Operation [{} - {}] partially completed ({}/{}) for WorkOrder {}",
                    operation.getSequence(),
                    operation.getOperationName(),
                    newCompleted,
                    operation.getPlannedQuantity(),
                    workOrder.getWorkOrderNumber());
        }

        workOrderOperationRepository.save(operation);

        // Update WorkOrder progress
        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);
        workOrder.setCompletedQuantity(totalCompleted);

        // Update WorkOrder status if first operation being started
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.RELEASED ||
                workOrder.getWorkOrderStatus() == WorkOrderStatus.SCHEDULED) {
            workOrder.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
            workOrder.setActualStartDate(new Date());
        }

        workOrderRepository.save(workOrder);

        // Audit
        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_COMPLETED,
                "operation",
                currentCompleted.toString(),
                newCompleted.toString(),
                "Operation partial completion (" + batchQty + " units): " +
                        (partialCompleteDTO.getRemarks() != null ? partialCompleteDTO.getRemarks() : "")
        );
    }

    @Transactional
    @Override
    public void completeOperation(Long operationId, BigDecimal completedQty) {

        logger.info("Completing WorkOrderOperation id={} with qty={}", operationId, completedQty);

        //  Fetch operation
        WorkOrderOperation operation =
                workOrderOperationRepository.findById(operationId)
                        .orElseThrow(() -> {
                            logger.error("Operation not found id={}", operationId);
                            return new EntityNotFoundException("Operation not found");
                        });

        WorkOrder workOrder = operation.getWorkOrder();

        //  Validate operation status
        if (operation.getStatus() != OperationStatus.IN_PROGRESS) {
            logger.warn(
                    "Cannot complete operation {} for WorkOrder {} due to status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    operation.getStatus()
            );
            throw new IllegalStateException("Only IN_PROGRESS operations can be completed");
        }

        //  Validate quantity
        if (completedQty == null || completedQty.compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Invalid completed quantity {} for operation {}", completedQty, operationId);
            throw new IllegalArgumentException("Completed quantity must be greater than zero");
        }

        if (!Boolean.TRUE.equals(operation.getAllowOverCompletion()) &&
                completedQty.compareTo(operation.getPlannedQuantity()) > 0) {

            logger.warn(
                    "Over-completion not allowed for operation {} (planned={}, completed={})",
                    operation.getSequence(),
                    operation.getPlannedQuantity(),
                    completedQty
            );
            throw new IllegalStateException("Completed quantity exceeds planned quantity");
        }

        //  Complete operation
        operation.setCompletedQuantity(completedQty);
        operation.setStatus(OperationStatus.COMPLETED);
        operation.setActualEndDate(new Date());

        workOrderOperationRepository.save(operation);

        logger.info(
                "Operation [{} - {}] COMPLETED for WorkOrder {}",
                operation.getSequence(),
                operation.getOperationName(),
                workOrder.getWorkOrderNumber()
        );

        // Unlock downstream operations
        unlockEligibleDependents(operation, completedQty, workOrder);

        // Update WorkOrder progress
        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);

        workOrder.setCompletedQuantity(totalCompleted);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} progress updated: completedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_COMPLETED,
                "operation",
                null,
                operation.getSequence() + " - " + operation.getOperationName(),
                "Operation completed with qty: " + completedQty
        );
    }

    @Transactional
    @Override
    public void completeWorkOrder(int workOrderId) {

        logger.info("Completing WorkOrder id={}", workOrderId);

        //  Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS) {
            logger.warn(
                    "Cannot complete WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only IN_PROGRESS WorkOrders can be completed"
            );
        }

        //  Validate all operations completed
        boolean pendingOperations =
                workOrderOperationRepository
                        .existsByWorkOrderAndStatusNot(
                                workOrder, OperationStatus.COMPLETED
                        );

        if (pendingOperations) {
            logger.warn(
                    "Cannot complete WorkOrder {}: not all operations are completed",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "All operations must be COMPLETED before completing WorkOrder"
            );
        }

        // Handle Backflushing
        if (workOrder.isAllowBackflush()) {
            IssueWorkOrderMaterialDTO autoIssueDTO = new IssueWorkOrderMaterialDTO();
            autoIssueDTO.setWorkOrderId(workOrder.getId());
            List<IssueWorkOrderMaterialDTO.MaterialIssueItem> issueItems = new ArrayList<>();

            for (WorkOrderMaterial material : workOrderMaterialRepository.findByWorkOrderId(workOrder.getId())) {
                if (material.getDeletedDate() != null) continue;

                BigDecimal issued = material.getIssuedQuantity() != null ? material.getIssuedQuantity() : BigDecimal.ZERO;
                BigDecimal scrapped = material.getScrappedQuantity() != null ? material.getScrappedQuantity() : BigDecimal.ZERO;
                BigDecimal goodIssued = issued.subtract(scrapped);

                if (goodIssued.compareTo(material.getNetRequiredQuantity()) < 0) {
                    BigDecimal gap = material.getNetRequiredQuantity().subtract(goodIssued);
                    IssueWorkOrderMaterialDTO.MaterialIssueItem item = new IssueWorkOrderMaterialDTO.MaterialIssueItem();
                    item.setWorkOrderMaterialId(material.getId());
                    item.setIssuedQuantity(gap);
                    item.setScrappedQuantity(BigDecimal.ZERO);
                    issueItems.add(item);
                }
            }
            if (!issueItems.isEmpty()) {
                autoIssueDTO.setMaterials(issueItems);
                logger.info("Auto-Backflushing {} materials for WorkOrder {}", issueItems.size(), workOrder.getWorkOrderNumber());
                this.issueMaterials(autoIssueDTO);
            }
        }

        // Validate that all required material quantities are met
        // Check: issuedQty - scrappedQty (good consumed) >= netRequiredQuantity
        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

        for (WorkOrderMaterial material : materials) {
            if (material.getDeletedDate() != null) continue;

            BigDecimal issued = material.getIssuedQuantity() != null
                    ? material.getIssuedQuantity() : BigDecimal.ZERO;
            BigDecimal scrapped = material.getScrappedQuantity() != null
                    ? material.getScrappedQuantity() : BigDecimal.ZERO;
            BigDecimal goodIssued = issued.subtract(scrapped);

            if (goodIssued.compareTo(material.getNetRequiredQuantity()) < 0) {
                logger.warn(
                        "Cannot complete WorkOrder {}: material {} has insufficient good qty. Required: {}, Good issued: {} (issued {} - scrapped {})",
                        workOrder.getWorkOrderNumber(),
                        material.getComponent().getItemCode(),
                        material.getNetRequiredQuantity(),
                        goodIssued,
                        issued,
                        scrapped
                );
                throw new IllegalStateException(
                        "Material '" + material.getComponent().getItemCode() +
                        "' has insufficient good quantity. Required: " + material.getNetRequiredQuantity() +
                        ", Available: " + goodIssued
                );
            }
        }

        // Finalize quantities
        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);

        // Compute aggregated actual cost
        BigDecimal totalMaterialCost = BigDecimal.ZERO;
        for (WorkOrderMaterial material : materials) {
            if (material.getDeletedDate() != null) continue;
            BigDecimal consumed = material.getConsumedQuantity() != null ? material.getConsumedQuantity() : BigDecimal.ZERO;
            com.nextgenmanager.nextgenmanager.items.model.InventoryItem item = material.getComponent();
            double unitCost = item.getProductFinanceSettings() != null ? item.getProductFinanceSettings().getStandardCost() : 0.0;
            totalMaterialCost = totalMaterialCost.add(BigDecimal.valueOf(unitCost).multiply(consumed));
        }

        BigDecimal totalOperationCost = BigDecimal.ZERO;
        List<WorkOrderOperation> operations = workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());
        for (WorkOrderOperation op : operations) {
            if (op.getWorkCenter() != null && op.getWorkCenter().getMachineCostPerHour() != null) {
                BigDecimal hoursPerUnit = BigDecimal.ZERO;
                if (op.getRoutingOperation() != null && op.getRoutingOperation().getRunTime() != null) {
                     hoursPerUnit = op.getRoutingOperation().getRunTime().divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                }
                BigDecimal totalHours = hoursPerUnit.multiply(op.getCompletedQuantity());
                BigDecimal opCost = totalHours.multiply(op.getWorkCenter().getMachineCostPerHour());
                
                if (op.getWorkCenter().getOverheadPercentage() != null) {
                    BigDecimal overheadFactor = BigDecimal.ONE.add(op.getWorkCenter().getOverheadPercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                    opCost = opCost.multiply(overheadFactor);
                }
                totalOperationCost = totalOperationCost.add(opCost);
            }
        }

        BigDecimal totalCost = totalMaterialCost.add(totalOperationCost);
        BigDecimal realUnitCost = BigDecimal.ZERO;
        if (totalCompleted.compareTo(BigDecimal.ZERO) > 0) {
            realUnitCost = totalCost.divide(totalCompleted, 2, RoundingMode.HALF_UP);
        }

        // Add to inventory
        if (totalCompleted.compareTo(BigDecimal.ZERO) > 0) {
            com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest addInvReq = new com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest();
            addInvReq.setInventoryItemId(workOrder.getBom().getParentInventoryItem().getInventoryItemId());
            addInvReq.setProcurementDecision(com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision.WORK_ORDER);
            addInvReq.setReferenceId((long) workOrder.getId());
            addInvReq.setQuantity(totalCompleted.doubleValue());
            addInvReq.setCostPerUnit(realUnitCost.doubleValue());
            addInvReq.setCreatedBy("System");
            inventoryInstanceService.addInventory(addInvReq);
            logger.info("Added {} units to inventory for WorkOrder {} with cost ${}", totalCompleted, workOrder.getWorkOrderNumber(), realUnitCost);
        }

        workOrder.setCompletedQuantity(totalCompleted);

        //  Complete Work Order
        workOrder.setWorkOrderStatus(WorkOrderStatus.COMPLETED);
        workOrder.setActualEndDate(new Date());

        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} COMPLETED successfully with completedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.COMPLETED,
                "status",
                "IN_PROGRESS",
                "COMPLETED",
                "WorkOrder Completed"
        );
    }

    @Transactional
    @Override
    public void closeWorkOrder(int workOrderId) {

        logger.info("Closing WorkOrder id={}", workOrderId);

        // Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.COMPLETED) {
            logger.warn(
                    "Cannot close WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only COMPLETED WorkOrders can be closed"
            );
        }

        //  Safety check: no operation IN_PROGRESS (defensive)
        boolean opInProgress =
                workOrderOperationRepository
                        .existsByWorkOrderAndStatus(
                                workOrder, OperationStatus.IN_PROGRESS
                        );

        if (opInProgress) {
            logger.error(
                    "Close blocked: operation still IN_PROGRESS for WorkOrder {}",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "Cannot close WorkOrder with operations in progress"
            );
        }

        //  Safety check: no pending materials
        boolean pendingMaterials =
                workOrderMaterialRepository
                        .existsByWorkOrderAndIssueStatusNot(
                                workOrder, MaterialIssueStatus.ISSUED
                        );

        if (pendingMaterials) {
            logger.error(
                    "Close blocked: materials not fully issued for WorkOrder {}",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "Cannot close WorkOrder with pending material issues"
            );
        }

        // Close Work Order
        workOrder.setWorkOrderStatus(WorkOrderStatus.CLOSED);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} CLOSED successfully",
                workOrder.getWorkOrderNumber()
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.CLOSED,
                "status",
                "COMPLETED",
                "CLOSED",
                "WorkOrder Closed"
        );
    }

    @Transactional
    @Override
    public void cancelWorkOrder(int workOrderId) {

        logger.info("Cancelling WorkOrder id={}", workOrderId);

        // Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        WorkOrderStatus status = workOrder.getWorkOrderStatus();

        // Status guard
        if (status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CLOSED) {
            logger.warn(
                    "Cancel rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    status
            );
            throw new IllegalStateException(
                    "Completed or Closed WorkOrders cannot be cancelled"
            );
        }

        // Ensure no operation IN_PROGRESS
        boolean opInProgress =
                workOrderOperationRepository
                        .existsByWorkOrderAndStatus(
                                workOrder, OperationStatus.IN_PROGRESS
                        );

        if (opInProgress) {
            logger.warn(
                    "Cancel blocked: operation IN_PROGRESS for WorkOrder {}",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "Cannot cancel WorkOrder while an operation is in progress"
            );
        }

        // Cancel inventory requests
        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());
        for (WorkOrderMaterial material : materials) {
            if (material.getInventoryRequestId() != null) {
                try {
                    inventoryInstanceService.rejectInventoryRequest(material.getInventoryRequestId(), "System", "Work Order Cancelled");
                } catch (Exception e) {
                    logger.warn("Could not reject inventory request {} for WorkOrder {}: {}", material.getInventoryRequestId(), workOrder.getWorkOrderNumber(), e.getMessage());
                }
            }
        }

        // Cancel operations (non-completed)
        List<WorkOrderOperation> operations =
                workOrderOperationRepository
                        .findByWorkOrder(workOrder);

        for (WorkOrderOperation op : operations) {
            if (op.getStatus() != OperationStatus.COMPLETED) {
                op.setStatus(OperationStatus.CANCELLED);
            }
        }

        workOrderOperationRepository.saveAll(operations);

        // Cancel Work Order
        workOrder.setWorkOrderStatus(WorkOrderStatus.CANCELLED);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} CANCELLED successfully",
                workOrder.getWorkOrderNumber()
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.CANCELLED,
                "status",
                status.toString(),
                "CANCELLED",
                "WorkOrder Cancelled"
        );
    }

    @Transactional
    @Override
    public void shortCloseWorkOrder(int workOrderId, String remarks) {

        logger.info("Short-closing WorkOrder id={}", workOrderId);

        // ── Fetch Work Order ─────────────────────────────────────────────────
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        WorkOrderStatus currentStatus = workOrder.getWorkOrderStatus();

        // ── Status guard: only RELEASED or IN_PROGRESS can be short-closed ──
        if (currentStatus != WorkOrderStatus.RELEASED
                && currentStatus != WorkOrderStatus.IN_PROGRESS) {
            logger.warn(
                    "Short-close rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    currentStatus
            );
            throw new IllegalStateException(
                    "WorkOrder can only be short-closed when RELEASED or IN_PROGRESS. Current: " + currentStatus
            );
        }

        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());
        List<WorkOrderOperation> operations = workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

        // ── Step 1: Backflush if enabled ─────────────────────────────────────
        // For backflush WOs, auto-issue materials proportional to what was
        // actually completed so that cost calculations are accurate.
        if (workOrder.isAllowBackflush()) {
            BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);
            BigDecimal plannedQty = workOrder.getPlannedQuantity();

            if (totalCompleted.compareTo(BigDecimal.ZERO) > 0 && plannedQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal completionRatio = totalCompleted.divide(plannedQty, 10, RoundingMode.HALF_UP);

                IssueWorkOrderMaterialDTO autoIssueDTO = new IssueWorkOrderMaterialDTO();
                autoIssueDTO.setWorkOrderId(workOrder.getId());
                List<IssueWorkOrderMaterialDTO.MaterialIssueItem> issueItems = new ArrayList<>();

                for (WorkOrderMaterial material : materials) {
                    if (material.getDeletedDate() != null) continue;

                    BigDecimal issued = material.getIssuedQuantity() != null ? material.getIssuedQuantity() : BigDecimal.ZERO;
                    BigDecimal proportionalNeed = material.getNetRequiredQuantity()
                            .multiply(completionRatio)
                            .setScale(5, RoundingMode.HALF_UP);
                    BigDecimal gap = proportionalNeed.subtract(issued);

                    if (gap.compareTo(BigDecimal.ZERO) > 0) {
                        IssueWorkOrderMaterialDTO.MaterialIssueItem item = new IssueWorkOrderMaterialDTO.MaterialIssueItem();
                        item.setWorkOrderMaterialId(material.getId());
                        item.setIssuedQuantity(gap);
                        item.setScrappedQuantity(BigDecimal.ZERO);
                        issueItems.add(item);
                    }
                }

                if (!issueItems.isEmpty()) {
                    autoIssueDTO.setMaterials(issueItems);
                    logger.info("Short-close backflush: auto-issuing {} materials for WorkOrder {}",
                            issueItems.size(), workOrder.getWorkOrderNumber());
                    try {
                        this.issueMaterials(autoIssueDTO);
                    } catch (Exception e) {
                        logger.warn("Backflush during short-close failed for WorkOrder {}: {}",
                                workOrder.getWorkOrderNumber(), e.getMessage());
                    }
                    // Refresh materials after auto-issue
                    materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());
                }
            }
        }

        // ── Step 2: Add finished goods to inventory (partial output) ─────────
        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);

        if (totalCompleted.compareTo(BigDecimal.ZERO) > 0) {
            // Calculate actual cost for the partial output
            BigDecimal totalMaterialCost = BigDecimal.ZERO;
            for (WorkOrderMaterial material : materials) {
                if (material.getDeletedDate() != null) continue;
                BigDecimal consumed = material.getConsumedQuantity() != null ? material.getConsumedQuantity() : BigDecimal.ZERO;
                com.nextgenmanager.nextgenmanager.items.model.InventoryItem item = material.getComponent();
                double unitCost = item.getProductFinanceSettings() != null ? item.getProductFinanceSettings().getStandardCost() : 0.0;
                totalMaterialCost = totalMaterialCost.add(BigDecimal.valueOf(unitCost).multiply(consumed));
            }

            BigDecimal totalOperationCost = BigDecimal.ZERO;
            for (WorkOrderOperation op : operations) {
                if (op.getWorkCenter() != null && op.getWorkCenter().getMachineCostPerHour() != null) {
                    BigDecimal hoursPerUnit = BigDecimal.ZERO;
                    if (op.getRoutingOperation() != null && op.getRoutingOperation().getRunTime() != null) {
                        hoursPerUnit = op.getRoutingOperation().getRunTime()
                                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                    }
                    BigDecimal completedQty = op.getCompletedQuantity() != null ? op.getCompletedQuantity() : BigDecimal.ZERO;
                    BigDecimal totalHours = hoursPerUnit.multiply(completedQty);
                    BigDecimal opCost = totalHours.multiply(op.getWorkCenter().getMachineCostPerHour());

                    if (op.getWorkCenter().getOverheadPercentage() != null) {
                        BigDecimal overheadFactor = BigDecimal.ONE.add(
                                op.getWorkCenter().getOverheadPercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                        opCost = opCost.multiply(overheadFactor);
                    }
                    totalOperationCost = totalOperationCost.add(opCost);
                }
            }

            BigDecimal totalCost = totalMaterialCost.add(totalOperationCost);
            BigDecimal realUnitCost = totalCost.divide(totalCompleted, 2, RoundingMode.HALF_UP);

            com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest addInvReq =
                    new com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest();
            addInvReq.setInventoryItemId(workOrder.getBom().getParentInventoryItem().getInventoryItemId());
            addInvReq.setProcurementDecision(
                    com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision.WORK_ORDER);
            addInvReq.setReferenceId((long) workOrder.getId());
            addInvReq.setQuantity(totalCompleted.doubleValue());
            addInvReq.setCostPerUnit(realUnitCost.doubleValue());
            addInvReq.setCreatedBy("System");
            inventoryInstanceService.addInventory(addInvReq);

            logger.info("Short-close: Added {} units to inventory for WorkOrder {} with cost ₹{}",
                    totalCompleted, workOrder.getWorkOrderNumber(), realUnitCost);
        }

        workOrder.setCompletedQuantity(totalCompleted);

        // ── Step 3: Return unused materials to store ─────────────────────────
        // Materials that were issued but not consumed should go back to inventory
        for (WorkOrderMaterial material : materials) {
            if (material.getDeletedDate() != null) continue;

            BigDecimal issued = material.getIssuedQuantity() != null ? material.getIssuedQuantity() : BigDecimal.ZERO;
            BigDecimal consumed = material.getConsumedQuantity() != null ? material.getConsumedQuantity() : BigDecimal.ZERO;
            BigDecimal scrapped = material.getScrappedQuantity() != null ? material.getScrappedQuantity() : BigDecimal.ZERO;

            BigDecimal returnable = issued.subtract(consumed).subtract(scrapped);

            if (returnable.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest returnReq =
                            new com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest();
                    returnReq.setInventoryItemId(material.getComponent().getInventoryItemId());
                    returnReq.setProcurementDecision(
                            com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision.WORK_ORDER);
                    returnReq.setReferenceId((long) workOrder.getId());
                    returnReq.setQuantity(returnable.doubleValue());
                    double unitCost = material.getComponent().getProductFinanceSettings() != null
                            ? material.getComponent().getProductFinanceSettings().getStandardCost() : 0.0;
                    returnReq.setCostPerUnit(unitCost);
                    returnReq.setCreatedBy("System");

                    inventoryInstanceService.addInventory(returnReq);

                    logger.info("Short-close: Returned {} units of {} to store for WorkOrder {}",
                            returnable,
                            material.getComponent().getItemCode(),
                            workOrder.getWorkOrderNumber());

                    auditService.record(
                            workOrder,
                            WorkOrderEventType.MATERIAL_RETURNED,
                            "material",
                            material.getComponent().getItemCode(),
                            returnable.toPlainString(),
                            "Returned unused material to store on short-close"
                    );
                } catch (Exception e) {
                    logger.warn("Could not return material {} for WorkOrder {}: {}",
                            material.getComponent().getItemCode(),
                            workOrder.getWorkOrderNumber(),
                            e.getMessage());
                }
            }

            // ── Step 3b: Cancel remaining unissued inventory reservations ────
            if (material.getInventoryRequestId() != null
                    && material.getIssueStatus() != com.nextgenmanager.nextgenmanager.production.enums.MaterialIssueStatus.ISSUED) {
                try {
                    inventoryInstanceService.rejectInventoryRequest(
                            material.getInventoryRequestId(),
                            "System",
                            "Work Order Short-Closed: " + (remarks != null ? remarks : "")
                    );
                    logger.info("Short-close: Cancelled inventory request {} for material {} on WorkOrder {}",
                            material.getInventoryRequestId(),
                            material.getComponent().getItemCode(),
                            workOrder.getWorkOrderNumber());
                } catch (Exception e) {
                    logger.warn("Could not reject inventory request {} for WorkOrder {}: {}",
                            material.getInventoryRequestId(),
                            workOrder.getWorkOrderNumber(),
                            e.getMessage());
                }
            }
        }

        // ── Step 4: Cancel non-completed operations ──────────────────────────
        for (WorkOrderOperation op : operations) {
            if (op.getStatus() != OperationStatus.COMPLETED) {
                op.setStatus(OperationStatus.CANCELLED);
            }
        }
        workOrderOperationRepository.saveAll(operations);

        // ── Step 5: Finalize work order ──────────────────────────────────────
        workOrder.setWorkOrderStatus(WorkOrderStatus.SHORT_CLOSED);
        workOrder.setActualEndDate(new Date());
        workOrder.setRemarks(
                (workOrder.getRemarks() != null ? workOrder.getRemarks() + " | " : "")
                        + "SHORT-CLOSED: " + (remarks != null ? remarks : "No reason provided")
        );
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} SHORT_CLOSED successfully with completedQty={} out of plannedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted,
                workOrder.getPlannedQuantity()
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.SHORT_CLOSED,
                "status",
                currentStatus.toString(),
                "SHORT_CLOSED",
                "WorkOrder Short-Closed. Reason: " + (remarks != null ? remarks : "N/A")
                        + ". Completed: " + totalCompleted + "/" + workOrder.getPlannedQuantity()
        );
    }

    @Transactional
    @Override
    public void softDeleteWorkOrder(int workOrderId, String reason) {

        logger.info("Soft deleting WorkOrder id={}", workOrderId);

        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        WorkOrderStatus status = workOrder.getWorkOrderStatus();

        // Status guard
        if (!(status == WorkOrderStatus.CREATED || status == WorkOrderStatus.CANCELLED)) {
            logger.warn(
                    "Soft delete rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    status
            );
            throw new IllegalStateException(
                    "Only CREATED or CANCELLED WorkOrders can be deleted"
            );
        }

        Date now = new Date();

        // Soft delete children first (defensive)
        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrder(workOrder);

        for (WorkOrderOperation op : operations) {
            op.setDeletedDate(now);
        }
        workOrderOperationRepository.saveAll(operations);

        List<WorkOrderMaterial> materials =
                workOrderMaterialRepository.findByWorkOrder(workOrder);

        for (WorkOrderMaterial mat : materials) {
            mat.setDeletedDate(now);
        }
        workOrderMaterialRepository.saveAll(materials);

        //  Soft delete work order
        workOrder.setDeletedDate(now);
        workOrderRepository.save(workOrder);

        // Audit
        auditService.record(
                workOrder,
                WorkOrderEventType.UPDATED,
                "deletedDate",
                null,
                now.toString(),
                "WorkOrder soft deleted: " + reason
        );

        logger.info(
                "WorkOrder {} soft deleted successfully",
                workOrder.getWorkOrderNumber()
        );
    }

    public List<WorkOrderHistoryDTO> getWorkOrderHistory(int workOrderId){
            logger.info("Fetching history for WorkOrder id={}", workOrderId);
            WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                    .orElseThrow(() -> {
                        logger.error("WorkOrder not found id={}", workOrderId);
                        return new EntityNotFoundException("WorkOrder not found");
                    });

            List<WorkOrderHistoryDTO> history = auditService.getHistoryForWorkOrder(
                workOrderId
            );

            logger.info(
                    "History fetched for WorkOrder {}: {} events",
                    workOrder.getWorkOrderNumber(),
                    history.size()
            );
            return history;
    }

    // ──────────────────────────────── Scheduling ────────────────────────────────

    @Autowired
    private com.nextgenmanager.nextgenmanager.production.service.scheduling.ProductionSchedulerService productionSchedulerService;

    @Override
    @Transactional
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId) {
        return productionSchedulerService.scheduleWorkOrder(workOrderId);
    }

    @Override
    @Transactional
    public ScheduleResultDTO rescheduleWorkOrder(int workOrderId, Date newStartDate) {
        return productionSchedulerService.rescheduleWorkOrder(workOrderId, newStartDate);
    }

}
